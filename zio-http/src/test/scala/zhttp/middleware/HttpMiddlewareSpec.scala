package zhttp.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.HttpMiddleware
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.environment.{TestClock, TestConsole}
import zio.test.{DefaultRunnableSpec, assertM}
import zio.{UIO, ZIO, console}

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
            val program    = run(app @@ middleware).map(_.headers)
            assertM(program)(equalTo(List(Header("KeyA", "ValueA"), Header("KeyB", "ValueB"))))
          } +
          testM("add and remove header") {
            val middleware = addHeader("KeyA", "ValueA") ++ removeHeader("KeyA")
            val program    = run(app @@ middleware).map(_.headers)
            assertM(program)(equalTo(Nil))
          }
      } +
      suite("CSRF Builder") {
        testM("Should set set-cookie header") {
          val app: HttpApp[Any, Nothing]      = HttpApp.collect { case Method.GET -> !! / "health" =>
            Response.ok
          }
          def run(app: HttpApp[Any, Nothing]) = {
            app { Request(url = URL(!! / "health")) }
          }
          val middleware                      = CSRFBuilder.csrfBuilder(UIO("token"))
          val program                         = run(app @@ middleware).map(_.headers)
          assertM(program)(equalTo(List(Header("Set-Cookie", "csrf-token=token"))))
        } +
          testM("Should set response status to UNAUTHORIZED, if CSRF header is not set") {
            val app: HttpApp[Any, Nothing]      = HttpApp.collect { case Method.GET -> !! / "health" =>
              Response.ok
            }
            def run(app: HttpApp[Any, Nothing]) = {
              app {
                Request(
                  url = URL(!! / "health"),
                  headers = List(Header(HttpHeaderNames.COOKIE, Cookie(name = "csrf-token", content = "token").encode)),
                )
              }
            }
            val middleware                      = CSRFBuilder.csrfChecker("x-csrf")
            val program                         = run(app @@ middleware).map(_.status)
            assertM(program)(equalTo(Status.UNAUTHORIZED))
          } +
          testM("Should set response status to OK, if CSRF header is set correctly") {
            val app: HttpApp[Any, Nothing]      = HttpApp.collect { case Method.GET -> !! / "health" =>
              Response.ok
            }
            def run(app: HttpApp[Any, Nothing]) = {
              app {
                Request(
                  url = URL(!! / "health"),
                  headers = List(
                    Header(HttpHeaderNames.COOKIE, Cookie(name = "csrf-token", content = "token").encode),
                    Header("x-csrf", "token"),
                  ),
                )
              }
            }
            val middleware                      = CSRFBuilder.csrfChecker("x-csrf")
            val program                         = run(app @@ middleware).map(_.status)
            assertM(program)(equalTo(Status.OK))
          }
      }
  }
}
