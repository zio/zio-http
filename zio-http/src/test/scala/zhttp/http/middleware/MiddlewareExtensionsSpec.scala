package zhttp.http.middleware

import zhttp.http._
import zhttp.http.middleware.Middleware._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{TestClock, TestConsole}

object MiddlewareExtensionsSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  private val app: HttpApp[Any with Clock, Nothing] = Http.collectZIO[Request] { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }
  private val midA                                  = Middleware.addHeader("X-Custom", "A")
  private val midB                                  = Middleware.addHeader("X-Custom", "B")
  private val basicHS                               = Headers.basicAuthorizationHeader("user", "resu")
  private val basicHF                               = Headers.basicAuthorizationHeader("user", "user")
  private val basicAuthM                            = Middleware.basicAuth { case (u, p) => p.toString.reverse == u }

  def spec = suite("HttpMiddleware") {

    suite("debug") {
      testM("log status method url and time") {
        val program = run(app @@ debug) *> TestConsole.output
        assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      } +
        testM("log 404 status method url and time") {
          val program = run(Http.empty ++ Http.notFound @@ debug) *> TestConsole.output
          assertM(program)(equalTo(Vector("404 GET /health 0ms\n")))
        }
    } +
      suite("when") {
        testM("condition is true") {
          val program = run(app @@ debug.when(_ => true)) *> TestConsole.output
          assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
        } +
          testM("condition is false") {
            val log = run(app @@ debug.when(_ => false)) *> TestConsole.output
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
      suite("ifThenElseZIO") {
        testM("if the condition is true take first") {
          val app = (Http.ok @@ ifThenElseZIO(condM(true))(midA, midB)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false take 2nd") {
            val app =
              (Http.ok @@ ifThenElseZIO(condM(false))(midA, midB)) getHeader "X-Custom"
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
          val app = (Http.ok @@ whenZIO(condM(true))(midA)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false don't apply any middleware") {
            val app = (Http.ok @@ whenZIO(condM(false))(midA)) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("when") {
        testM("if the condition is true apple middleware") {
          val app = Http.ok @@ Middleware.when(cond(true))(midA) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false don't apply the middleware") {
            val app = Http.ok @@ Middleware.when(cond(false))(midA) getHeader "X-Custom"
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
      suite("cookie") {
        testM("addCookie") {
          val cookie = Cookie("test", "testValue")
          val app    = (Http.ok @@ addCookie(cookie)).getHeader("set-cookie")
          assertM(app(Request()))(
            equalTo(Some(cookie.encode)),
          )
        } +
          testM("addCookieM") {
            val cookie = Cookie("test", "testValue")
            val app    =
              (Http.ok @@ addCookieM(UIO(cookie))).getHeader("set-cookie")
            assertM(app(Request()))(
              equalTo(Some(cookie.encode)),
            )
          }
      }
  }

  private def cond(flg: Boolean) = (_: Any, _: Any, _: Any) => flg

  private def condM(flg: Boolean) = (_: Any, _: Any, _: Any) => UIO(flg)

  private def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
