package zhttp.middleware

import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test.environment.{TestClock, TestConsole}
import zio.test.{DefaultRunnableSpec, assert, assertM}
import zio.{UIO, ZIO, console}

object MiddlewareSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  def spec = suite("HttpMiddleware") {
    import Middleware._

    suite("debug") {
      testM("log status method url and time") {
        val program = run(app @@ debug) *> TestConsole.output
        assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      } +
        testM("log 404 status method url and time") {
          val program = run(Http.empty @@ debug.withEmpty) *> TestConsole.output
          assertM(program)(equalTo(Vector("404 GET /health 0ms\n")))
        }
    } +
      suite("withEmpty") {
        testM("log 404 status method url and time") {
          val program = run(Http.empty @@ debug.withEmpty) *> TestConsole.output
          assertM(program)(equalTo(Vector("404 GET /health 0ms\n")))
        }
      } +
      suite("when") {
        testM("condition is true") {
          val program = run(app @@ debug.when((_, _, _) => true)) *> TestConsole.output
          assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
        } +
          testM("condition is false") {
            val log = run(app @@ debug.when((_, _, _) => false)) *> TestConsole.output
            assertM(log)(equalTo(Vector()))
          }
      } +
      suite("race") {
        testM("achieved") {
          val program = run(app @@ timeout(5 seconds)).map(_.status)
          assertM(program)(equalTo(Status.OK))
        } +
          testM("un-achieved") {
            val program = run(app @@ timeout(500 millis)).map(_.status)
            assertM(program)(equalTo(Status.REQUEST_TIMEOUT))
          }
      } +
      suite("Lazy") {
        testM("basic usage") {
          var bool      = true
          def selectApp = () =>
            if (bool) {
              identity
            } else {
              fromApp(Http.status(Status.REQUEST_TIMEOUT))
            }

          val wrappedApp = app @@ lazyMiddleware(selectApp)
          for {
            res200 <- assertM(run(wrappedApp).map(_.status))(equalTo(Status.OK))
            _ = { bool = false }
            res408 <- assertM(run(wrappedApp).map(_.status))(equalTo(Status.REQUEST_TIMEOUT))
          } yield res200 && res408

        }
      } +
      suite("CircuitBreaker") {
        val fallbackApp = Http.status(Status.SERVICE_UNAVAILABLE)
        testM("basic usage") {
          val thresholds = Thresholds("cb1")
          val program    = run(app @@ circuitBreaker(thresholds, fallbackApp)).map(_.status)
          assertM(program)(equalTo(Status.OK))
        } +
          testM("state transition: close -> open -> half_open -> open -> close") {
            import CircuitBreaker._
            val thresholds = Thresholds(
              "cb2",
              failureRateThreshold = 30,
              permittedNumberOfCallsInHalfOpenState = 2,
              slidingWindowSize = 3,
              minimumNumberOfCalls = 2,
              waitDurationInOpenState = 300,
            )
            val instance   = CircuitBreaker.instance(thresholds)
            def run2[R, E](app1: HttpApp[R, E]): ZIO[TestClock with R, Option[E], (Status, Int, State)] =
              run(app1 @@ circuitBreaker(thresholds, Http.status(Status.SERVICE_UNAVAILABLE)))
                .map(s => (s.status, instance.errorRatio(), instance.checkCurrentState))

            def program200 = run2(app)
            def program408 = run2(Http.status(Status.REQUEST_TIMEOUT))

            for {
              res1 <- assertM(program408)(equalTo((Status.REQUEST_TIMEOUT, 0, Closed)))     // 1/1 (below minimum)
              res2 <- assertM(program408)(equalTo((Status.REQUEST_TIMEOUT, 100, Open)))     // 2/2
              res3 <- assertM(program200)(equalTo((Status.SERVICE_UNAVAILABLE, 100, Open))) // 2/2 (no count)
              _ = Thread.sleep(300) // wait duration in OpenState
              res4 <- assertM(program408)(equalTo((Status.REQUEST_TIMEOUT, 0, HalfOpen))) // 1/1 (reset, below minimum)
              res5 <- assertM(program408)(equalTo((Status.REQUEST_TIMEOUT, 100, Open)))   // 2/2
              _ = Thread.sleep(300) // wait duration in OpenState
              res6 <- assertM(program200)(equalTo((Status.OK, 0, HalfOpen))) // 0/1
              res7 <- assertM(program200)(equalTo((Status.OK, 0, Closed)))   // 0/2
            } yield res1 && res2 && res3 && res4 && res5 && res6 && res7
          }
      } +
      suite("combine") {
        testM("before and after") {
          val middleware = runBefore(console.putStrLn("A")) ++ runAfter(console.putStrLn("B"))
          val program    = run(app @@ middleware) *> TestConsole.output
          assertM(program)(equalTo(Vector("A\n", "B\n")))
        } +
          testM("add headers twice") {
            val middleware = addHeader("KeyA", "ValueA") ++ addHeader("KeyB", "ValueB")
            val headers    = (Http.ok @@ middleware).getHeaderValues
            assertM(headers(Request()))(contains("ValueA") && contains("ValueB"))
          } +
          testM("add and remove header") {
            val middleware = addHeader("KeyA", "ValueA") ++ removeHeader("KeyA")
            val program    = (Http.ok @@ middleware) getHeader "KeyA"
            assertM(program(Request()))(isNone)
          }
      } +
      suite("ifThenElseM") {
        testM("if the condition is true take first") {
          val app = (Http.ok @@ ifThenElseM(condM(true))(midA, midB)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false take 2nd") {
            val app =
              (Http.ok @@ ifThenElseM(condM(false))(midA, midB)) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("ifThenElse") {
        testM("if the condition is true take first") {
          val app = Http.ok @@ ifThenElse(cond(true))(midA, midB) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false take 2nd") {
            val app = Http.ok @@ ifThenElse(cond(false))(midA, midB) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("whenM") {
        testM("if the condition is true apply middleware") {
          val app = (Http.ok @@ whenM(condM(true))(midA)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false don't apply any middleware") {
            val app = (Http.ok @@ whenM(condM(false))(midA)) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("when") {
        testM("if the condition is true apple middleware") {
          val app = Http.ok @@ when(cond(true))(midA) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false don't apply the middleware") {
            val app = Http.ok @@ when(cond(false))(midA) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("Authentication middleware") {
        suite("basicAuth") {
          testM("HttpApp is accepted if the basic authentication succeeds") {
            val app = (Http.ok @@ basicAuthM).getStatus
            assertM(app(Request().addHeaders(basicHS)))(equalTo(Status.OK))
          } +
            testM("Uses forbidden app if the basic authentication fails") {
              val app = (Http.ok @@ basicAuthM).getStatus
              assertM(app(Request().addHeaders(basicHF)))(equalTo(Status.FORBIDDEN))
            } +
            testM("Responses should have WWW-Authentication header if Basic Auth failed") {
              val app = Http.ok @@ basicAuthM getHeader "WWW-AUTHENTICATE"
              assertM(app(Request().addHeaders(basicHF)))(isSome)
            }
        }
      } +
      suite("cors") {
        // FIXME:The test should ideally pass with `Http.ok` also
        val app = Http.collect[Request] { case Method.GET -> !! / "success" => Response.ok } @@ cors()
        testM("OPTIONS request") {
          val request = Request(
            method = Method.OPTIONS,
            url = URL(!! / "success"),
            headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
          )

          val expected = Headers
            .accessControlAllowCredentials(true)
            .withAccessControlAllowMethods(Method.GET)
            .withAccessControlAllowOrigin("test-env")
            .withAccessControlAllowHeaders(
              CORS.DefaultCORSConfig.allowedHeaders.getOrElse(Set.empty).mkString(","),
            )
            .toList

          for {
            res <- app(request)
          } yield assert(res.getHeadersAsList)(hasSubset(expected)) &&
            assert(res.status)(equalTo(Status.NO_CONTENT))
        } +
          testM("GET request") {
            val request =
              Request(
                method = Method.GET,
                url = URL(!! / "success"),
                headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
              )

            val expected = Headers
              .accessControlExposeHeaders("*")
              .withAccessControlAllowOrigin("test-env")
              .withAccessControlAllowMethods(Method.GET)
              .withAccessControlAllowCredentials(true)
              .toList

            for {
              res <- app(request)
            } yield assert(res.getHeadersAsList)(hasSubset(expected))
          }
      }
  }

  private val app: HttpApp[Any with Clock, Nothing] = Http.collectM[Request] { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }
  private val midA                                  = Middleware.addHeader("X-Custom", "A")
  private val midB                                  = Middleware.addHeader("X-Custom", "B")
  private val basicHS                               = Headers.basicAuthorizationHeader("user", "resu")
  private val basicHF                               = Headers.basicAuthorizationHeader("user", "user")
  private val basicAuthM                            = Middleware.basicAuth { case (u, p) => p.toString.reverse == u }

  private def cond(flg: Boolean) = (_: Any, _: Any, _: Any) => flg

  private def condM(flg: Boolean) = (_: Any, _: Any, _: Any) => UIO(flg)

  private def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
