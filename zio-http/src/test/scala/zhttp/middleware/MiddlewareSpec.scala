package zhttp.middleware

import zhttp.http.Middleware.cors
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test.environment.{TestClock, TestConsole}
import zio.test.{DefaultRunnableSpec, assert, assertM}
import zio.{UIO, ZIO, console}

object MiddlewareSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  def cond(flg: Boolean) = (_: Any, _: Any, _: Any) => flg

  def condM(flg: Boolean) = (_: Any, _: Any, _: Any) => UIO(flg)

  def corsSpec = suite("cors") {
    testM("options request") {
      val app      = Http.collect[Request] { case Method.GET -> !! / "success" => Response.ok } @@ cors()
      val headers  = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env")
      val res      = app(Request(headers = headers)).map(_.getHeadersAsList)
      val expected = (
        Headers.accessControlAllowCredentials(true) ++
          Headers.accessControlAllowMethods(Method.GET) ++
          Headers.accessControlAllowOrigin("test-env") ++
          Headers.accessControlAllowHeaders(CORS.DefaultCORSConfig.allowedHeaders.getOrElse(Set.empty).mkString(", "))
      ).getHeadersAsList

      assertM(res)(hasSubset(expected))
    }
  }

  def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

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
            assertM(app(Request().addHeader(basicHS)))(equalTo(Status.OK))
          } +
            testM("Uses forbidden app if the basic authentication fails") {
              val app = (Http.ok @@ basicAuthM).getStatus
              assertM(app(Request().addHeader(basicHF)))(equalTo(Status.FORBIDDEN))
            } +
            testM("Responses sould have WWW-Authentication header if Basic Auth failed") {
              val app = Http.ok @@ basicAuthM getHeader "WWW-AUTHENTICATE"
              assertM(app(Request().addHeader(basicHF)))(isSome)
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

          val expected = (
            Headers.accessControlAllowCredentials(true) ++
              Headers.accessControlAllowMethods(Method.GET) ++
              Headers.accessControlAllowOrigin("test-env") ++
              Headers.accessControlAllowHeaders(
                CORS.DefaultCORSConfig.allowedHeaders.getOrElse(Set.empty).mkString(","),
              )
          ).getHeadersAsList

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

            val expected = (
              Headers.accessControlExposeHeaders("*") ++
                Headers.accessControlAllowOrigin("test-env") ++
                Headers.accessControlAllowMethods(Method.GET) ++
                Headers.accessControlAllowCredentials(true)
            ).getHeadersAsList

            for {
              res <- app(request)
            } yield assert(res.getHeadersAsList)(hasSubset(expected))
          }
      }
  }

  private val app: HttpApp[Any with Clock, Nothing] = Http.collectM[Request] { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }

  private val midA       = Middleware.addHeader("X-Custom", "A")
  private val midB       = Middleware.addHeader("X-Custom", "B")
  private val basicHS    = Headers.basicHttpAuthorization("user", "resu")
  private val basicHF    = Headers.basicHttpAuthorization("user", "user")
  private val basicAuthM = Middleware.basicAuth { case (u, p) => p.toString.reverse == u }
}
