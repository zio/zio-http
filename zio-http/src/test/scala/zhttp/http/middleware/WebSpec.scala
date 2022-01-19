package zhttp.http.middleware

import zhttp.http.Middleware._
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.test.Assertion._
import zio.test._

object WebSpec extends DefaultRunnableSpec with HttpAppTestExtensions { self =>
  private val app  = Http.collectZIO[Request] { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }
  private val midA = Middleware.addHeader("X-Custom", "A")
  private val midB = Middleware.addHeader("X-Custom", "B")

  def spec = suite("HttpMiddleware") {
    suite("headers suite") {
      test("addHeaders") {
        val middleware = addHeaders(Headers("KeyA", "ValueA") ++ Headers("KeyB", "ValueB"))
        val headers    = (Http.ok @@ middleware).getHeaderValues
        assertM(headers(Request()))(contains("ValueA") && contains("ValueB"))
      } +
        test("addHeader") {
          val middleware = addHeader("KeyA", "ValueA")
          val headers    = (Http.ok @@ middleware).getHeaderValues
          assertM(headers(Request()))(contains("ValueA"))
        } +
        test("updateHeaders") {
          val middleware = updateHeaders(_ => Headers("KeyA", "ValueA"))
          val headers    = (Http.ok @@ middleware).getHeaderValues
          assertM(headers(Request()))(contains("ValueA"))
        } +
        test("removeHeader") {
          val middleware = removeHeader("KeyA")
          val headers = (Http.succeed(Response.ok.setHeaders(Headers("KeyA", "ValueA"))) @@ middleware) getHeader "KeyA"
          assertM(headers(Request()))(isNone)
        }
    } +
      suite("debug") {
        test("log status method url and time") {
          val program = runApp(app @@ debug) *> TestConsole.output
          assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
        } +
          test("log 404 status method url and time") {
            val program = runApp(Http.empty ++ Http.notFound @@ debug) *> TestConsole.output
            assertM(program)(equalTo(Vector("404 GET /health 0ms\n")))
          }
      } +
      suite("when") {
        test("condition is true") {
          val program = runApp(self.app @@ debug.when(_ => true)) *> TestConsole.output
          assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
        } +
          test("condition is false") {
            val log = runApp(self.app @@ debug.when(_ => false)) *> TestConsole.output
            assertM(log)(equalTo(Vector()))
          }
      } +
      suite("whenZIO") {
        test("condition is true") {
          val program = runApp(self.app @@ debug.whenZIO(_ => UIO(true))) *> TestConsole.output
          assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
        } +
          test("condition is false") {
            val log = runApp(self.app @@ debug.whenZIO(_ => UIO(false))) *> TestConsole.output
            assertM(log)(equalTo(Vector()))
          }
      } +
      suite("race") {
        test("achieved") {
          val program = runApp(self.app @@ timeout(5 seconds)).map(_.status)
          assertM(program)(equalTo(Status.OK))
        } +
          test("un-achieved") {
            val program = runApp(self.app @@ timeout(500 millis)).map(_.status)
            assertM(program)(equalTo(Status.REQUEST_TIMEOUT))
          }
      } +
      suite("combine") {
        test("before and after") {
          val middleware = runBefore(Console.printLine("A"))
          val program    = runApp(self.app @@ middleware) *> TestConsole.output
          assertM(program)(equalTo(Vector("A\n")))
        } +
          test("add headers twice") {
            val middleware = addHeader("KeyA", "ValueA") ++ addHeader("KeyB", "ValueB")
            val headers    = (Http.ok @@ middleware).getHeaderValues
            assertM(headers(Request()))(contains("ValueA") && contains("ValueB"))
          } +
          test("add and remove header") {
            val middleware = addHeader("KeyA", "ValueA") ++ removeHeader("KeyA")
            val program    = (Http.ok @@ middleware) getHeader "KeyA"
            assertM(program(Request()))(isNone)
          }
      } +
      suite("ifRequestThenElseZIO") {
        test("if the condition is true take first") {
          val app = (Http.ok @@ ifRequestThenElseZIO(condM(true))(midA, midB)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false take 2nd") {
            val app =
              (Http.ok @@ ifRequestThenElseZIO(condM(false))(midA, midB)) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("ifRequestThenElse") {
        test("if the condition is true take first") {
          val app = Http.ok @@ ifRequestThenElse(cond(true))(midA, midB) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false take 2nd") {
            val app = Http.ok @@ ifRequestThenElse(cond(false))(midA, midB) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("whenRequestZIO") {
        test("if the condition is true apply middleware") {
          val app = (Http.ok @@ whenRequestZIO(condM(true))(midA)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false don't apply any middleware") {
            val app = (Http.ok @@ whenRequestZIO(condM(false))(midA)) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("whenRequest") {
        test("if the condition is true apple middleware") {
          val app = Http.ok @@ Middleware.whenRequest(cond(true))(midA) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false don't apply the middleware") {
            val app = Http.ok @@ Middleware.whenRequest(cond(false))(midA) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("cookie") {
        test("addCookie") {
          val cookie = Cookie("test", "testValue")
          val app    = (Http.ok @@ addCookie(cookie)).getHeader("set-cookie")
          assertM(app(Request()))(
            equalTo(Some(cookie.encode)),
          )
        } +
          test("addCookieM") {
            val cookie = Cookie("test", "testValue")
            val app    =
              (Http.ok @@ addCookieZIO(UIO(cookie))).getHeader("set-cookie")
            assertM(app(Request()))(
              equalTo(Some(cookie.encode)),
            )
          }
      } +
      suite("signCookies") {
        test("should sign cookies") {
          val cookie = Cookie("key", "value").withHttpOnly
          val app    = Http.ok.withSetCookie(cookie) @@ signCookies("secret") getHeader "set-cookie"
          assertM(app(Request()))(isSome(equalTo(cookie.sign("secret").encode)))
        } +
          test("sign cookies no cookie header") {
            val app = (Http.ok.addHeader("keyA", "ValueA") @@ signCookies("secret")).getHeaderValues
            assertM(app(Request()))(contains("ValueA"))
          }
      }
  }

  private def cond(flg: Boolean) = (_: Any) => flg

  private def condM(flg: Boolean) = (_: Any) => UIO(flg)

  private def runApp[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
