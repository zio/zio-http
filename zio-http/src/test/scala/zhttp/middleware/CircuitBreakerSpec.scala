package zhttp.middleware

import zhttp.http.CircuitBreaker.{Closed, HalfOpen, Open, State}
import zhttp.http.{Thresholds, _}
import zhttp.internal.HttpAppTestExtensions
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, assert, assertM}
import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicInteger

object CircuitBreakerSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  def spec = suite("CircuitBreakerMiddleware") {
    import Middleware._

    suite("basic behavior") {
      suite("switch Apps by state of CircuitBreaker") {
        testM("state is closed, original App will be run") {
          val thresholds = Thresholds(name())
          val program    = run(app @@ circuitBreaker(thresholds, app500)).map(_.status)
          assertM(program)(equalTo(Status.OK))
        } + testM("state is open, fallbackApp will be run") {
          val thresholds = Thresholds(name())
          setState(Open, thresholds)
          val program    = run(app @@ circuitBreaker(thresholds, app500)).map(_.status)
          assertM(program)(equalTo(Status.SERVICE_UNAVAILABLE))
        }
      } + suite("record status") {
        testM("count 408 and 500 as failure, and 200 as success") {
          val thresholds =
            Thresholds(
              name(),
              failureRateThreshold = 110, // never open
              slidingWindowSize = 4,
              minimumNumberOfCalls = 1,
            )

          def run2[R, E](app1: HttpApp[R, E], thresholds: Thresholds): ZIO[TestClock with R, Option[E], (Status, Int)] =
            run(app1 @@ circuitBreaker(thresholds, app500))
              .map(s => (s.status, CircuitBreaker.instance(thresholds).errorRatio()))

          for {
            res1 <- assertM(run2(app408, thresholds))(equalTo((Status.REQUEST_TIMEOUT, 100)))    // 1/1
            res2 <- assertM(run2(app, thresholds))(equalTo((Status.OK, 50)))                     // 1/2
            res3 <- assertM(run2(app500, thresholds))(equalTo((Status.SERVICE_UNAVAILABLE, 66))) // 2/3
          } yield res1 && res2 && res3
        }
      } + suite("change state") {
        test("closed to open") {
          val thresholds               =
            Thresholds(name(), slidingWindowSize = 2, failureRateThreshold = 60, minimumNumberOfCalls = 1)
          val instance: CircuitBreaker = CircuitBreaker.instance(thresholds)

          instance.putHttpStatus(200)
          val res1 = assert(instance.checkCurrentState)(equalTo(Closed))
          instance.putHttpStatus(408)
          val res2 = assert(instance.checkCurrentState)(equalTo(Closed))
          instance.putHttpStatus(408)
          val res3 = assert(instance.checkCurrentState)(equalTo(Open))
          res1 && res2 && res3
        }
      }
    } + suite("count based sliding window") {

      test("closed to open") {
        val thresholds = Thresholds(name(), slidingWindowSize = 2, failureRateThreshold = 60, minimumNumberOfCalls = 1)
        val instance: CircuitBreaker = CircuitBreaker.instance(thresholds)

        instance.putHttpStatus(200)
        val res1 = assert(instance.checkCurrentState)(equalTo(Closed))
        instance.putHttpStatus(408)
        val res2 = assert(instance.checkCurrentState)(equalTo(Closed))
        instance.putHttpStatus(408)
        val res3 = assert(instance.checkCurrentState)(equalTo(Open))
        res1 && res2 && res3
      } + test("open to half_open") {
        val thresholds =
          Thresholds(name(), slidingWindowSize = 2, failureRateThreshold = 60, waitDurationInOpenState = 100)

        setState(Open, thresholds)
        val instance = CircuitBreaker.instance(thresholds)

        Thread.sleep(100)
        assert(instance.checkCurrentState)(equalTo(HalfOpen))
      } + test("half_open to open by failure of permitted call") {
        lazy val instance = {
          val thresholds = Thresholds(
            name(),
            slidingWindowSize = 2,
            permittedNumberOfCallsInHalfOpenState = 2,
            failureRateThreshold = 60,
            minimumNumberOfCalls = 2,
            maxWaitDurationInHalfOpenState = 100,
          )

          val instance = CircuitBreaker.instance(thresholds)
          setState(HalfOpen, thresholds)
          instance
        }

        val res1 = assert(instance.checkCurrentState)(equalTo(HalfOpen))
        instance.putHttpStatus(408)
        val res2 = assert(instance.checkCurrentState)(equalTo(HalfOpen))
        instance.putHttpStatus(408)
        val res3 = assert(instance.checkCurrentState)(equalTo(Open))
        res1 && res2 && res3
      } + test("half_open to open by timeout") {
        lazy val instance = {
          val thresholds =
            Thresholds(
              name(),
              slidingWindowSize = 2,
              failureRateThreshold = 60,
              maxWaitDurationInHalfOpenState = 100,
            )

          setState(HalfOpen, thresholds)
          val instance = CircuitBreaker.instance(thresholds)
          instance.putHttpStatus(408)
          instance.putHttpStatus(408)
          instance
        }

        val res1 = assert(instance.checkCurrentState)(equalTo(HalfOpen))
        Thread.sleep(110)
        val res2 = assert(instance.checkCurrentState)(equalTo(Open))
        res1 && res2
      } + test("half_open to closed") {

        val thresholds = Thresholds(
          name(),
          slidingWindowSize = 2,
          permittedNumberOfCallsInHalfOpenState = 2,
          failureRateThreshold = 60,
          minimumNumberOfCalls = 2,
          maxWaitDurationInHalfOpenState = 100,
        )

        val instance: CircuitBreaker = CircuitBreaker.instance(thresholds)
        setState(HalfOpen, thresholds)

        val res1 = assert(instance.checkCurrentState)(equalTo(HalfOpen))
        instance.putHttpStatus(200)
        instance.putHttpStatus(200)
        val res2 = assert(instance.checkCurrentState)(equalTo(Closed))
        res1 && res2
      }
    }
// + suite("time based sliding window") {}
  }

  private val app: HttpApp[Any with Clock, Nothing] = Http.collectM[Request] { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }
  private val app500: HttpApp[Any, Nothing]         = Http.status(Status.SERVICE_UNAVAILABLE)
  private val app408: HttpApp[Any, Nothing]         = Http.status(Status.REQUEST_TIMEOUT)

  private def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    val value: ZIO[R, Option[E], Response[R, E]] = app {
      Request(url = URL(!! / "health"))
    }
    for {
      fib <- value.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

  private val nameCounter = new AtomicInteger()
  private def name()      = s"circuit-breaker-${nameCounter.getAndIncrement()}"

  private def setState(state: State, thresholds: Thresholds): Thresholds = {
    CircuitBreaker.instance(thresholds).setState(state)
    thresholds
  }
}
