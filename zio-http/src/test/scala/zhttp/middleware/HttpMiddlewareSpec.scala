package zhttp.middleware

import zhttp.http._
import zhttp.http.middleware.HttpMiddleware
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.environment.{TestClock, TestConsole}
import zio.test.{assertM, DefaultRunnableSpec}
import zio.{console, UIO, ZIO}
import zio.test.TestAspect.{ignore, nonFlaky}

object HttpMiddlewareSpec extends DefaultRunnableSpec {
  val app: HttpApp[Any with Clock, Nothing] = HttpApp.collectM { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }

  def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

  def spec = suite("HttpMiddleware") {
    suite("debug") {
      testM("log status method url and time") {
        val program = run(app @@ HttpMiddleware.debug) *> TestConsole.output
        assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      }
    } +
      suite("when") {
        testM("condition is true") {
          val program = run(app @@ HttpMiddleware.debug.when((_, _, _) => true)) *> TestConsole.output
          assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
        } +
          testM("condition is false") {
            val log = run(app @@ HttpMiddleware.debug.when((_, _, _) => false)) *> TestConsole.output
            assertM(log)(equalTo(Vector()))
          }
      } +
      suite("race") {
        testM("timeout achieved") {
          val program = run(app @@ HttpMiddleware.debug.race(HttpMiddleware.after(console.putStrLn("Came First")))) *>
            TestConsole.output

          assertM(program)(equalTo(Vector("Came First\n")))
        } @@ nonFlaky +
          testM("timeout un-achieved") {
            val middleware = HttpMiddleware.debug.race {
              HttpMiddleware.after(console.putStrLn("Came First").delay(1500 millis))
            }

            val program = run(app @@ middleware) *> TestConsole.output
            assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
          }
      } @@ ignore +
      suite("timeout") {
        testM("achieved") {
          val program = run(app @@ HttpMiddleware.timeout(5 seconds)).map(_.status)
          assertM(program)(equalTo(Status.OK))
        } +
          testM("unachived") {
            val program = run(app @@ HttpMiddleware.timeout(500 millis)).map(_.status)
            assertM(program)(equalTo(Status.REQUEST_TIMEOUT))
          }
      }

  }
}
