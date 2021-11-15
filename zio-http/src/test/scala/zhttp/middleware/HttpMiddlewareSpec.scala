package zhttp.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.HttpMiddleware
import zhttp.internal.HttpAppTestExtensions
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.environment.{TestClock, TestConsole}
import zio.test.{DefaultRunnableSpec, assertM}
import zio.{UIO, ZIO, console}

object HttpMiddlewareSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  val app: HttpApp[Any with Clock, Nothing] = HttpApp.collectM { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }

  val midA = HttpMiddleware.addHeader("X-Custom", "A")
  val midB = HttpMiddleware.addHeader("X-Custom", "B")

  def condM(flg: Boolean) = (_: Any, _: Any, _: Any) => UIO(flg)
  def cond(flg: Boolean)  = (_: Any, _: Any, _: Any) => flg

  val basicHS    = Header.basicHttpAuthorization("user", "resu")
  val basicHF    = Header.basicHttpAuthorization("user", "user")
  val basicAuthM = HttpMiddleware.basicAuth((u, p) => p.reverse == u)

  def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

  def spec = suite("HttpMiddleware") {
    import HttpMiddleware._

    suite("debug") {
      testM("log status method url and time") {
        val program = run(app @@ debug) *> TestConsole.output
        assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
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
            val program    = run(app @@ middleware).map(_.getHeaders)
            assertM(program)(
              equalTo(
                List(Header(HttpHeaderNames.CONTENT_LENGTH, "0"), Header("KeyA", "ValueA"), Header("KeyB", "ValueB")),
              ),
            )
          } +
          testM("add and remove header") {
            val middleware = addHeader("KeyA", "ValueA") ++ removeHeader("KeyA")
            val program    = run(app @@ middleware).map(_.getHeaders)
            assertM(program)(equalTo(List(Header(HttpHeaderNames.CONTENT_LENGTH, "0"))))
          }
      } +
      suite("ifThenElseM") {
        testM("if the condition is true take first") {
          val app = (HttpApp.ok @@ ifThenElseM(condM(true))(midA, midB)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false take 2nd") {
            val app =
              (HttpApp.ok @@ ifThenElseM(condM(false))(midA, midB)) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("ifThenElse") {
        testM("if the condition is true take first") {
          val app = HttpApp.ok @@ ifThenElse(cond(true))(midA, midB) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false take 2nd") {
            val app = HttpApp.ok @@ ifThenElse(cond(false))(midA, midB) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("whenM") {
        testM("if the condition is true apply middleware") {
          val app = (HttpApp.ok @@ whenM(condM(true))(midA)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false don't apply any middleware") {
            val app = (HttpApp.ok @@ whenM(condM(false))(midA)) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("when") {
        testM("if the condition is true apple middleware") {
          val app = HttpApp.ok @@ when(cond(true))(midA) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          testM("if the condition is false don't apply the middleware") {
            val app = HttpApp.ok @@ when(cond(false))(midA) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("Authentication middleware") {
        suite("basicAuth") {
          testM("HttpApp is accepted if the basic authentication succeeds") {
            val app = (HttpApp.ok @@ basicAuthM).getStatus
            assertM(app(Request().addHeaders(List(basicHS))))(equalTo(Status.OK))
          } +
            testM("Uses forbidden app if the basic authentication fails") {
              val app = (HttpApp.ok @@ basicAuthM).getStatus
              assertM(app(Request().addHeaders(List(basicHF))))(equalTo(Status.FORBIDDEN))
            } +
            testM("Responses sould have WWW-Authentication header if Basic Auth failed") {
              val app = HttpApp.ok @@ basicAuthM getHeader "WWW-AUTHENTICATE"
              assertM(app(Request().addHeaders(List(basicHF))))(isSome)
            }
        }
      }

  }
}
