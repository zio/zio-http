package zhttp.middleware

import zhttp.http._
import zhttp.http.middleware.HttpMiddleware
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.{equalTo, isNone, isSome}
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
      suite("ifThenElseM") {
        testM("if the condition is true take first") {
          val app =
            (HttpApp.ok @@ ifThenElseM((_, _, _) => UIO(true))(
              HttpMiddleware.addHeader("TestHeader", "left"),
              HttpMiddleware.addHeader("TestHeader", "right"),
            ))(Request())
          assertM(app.map(_.getHeaderValue("TestHeader")))(isSome(equalTo("left")))
        } +
          testM("if the condition is false take 2nd") {
            val app =
              (HttpApp.ok @@ ifThenElseM((_, _, _) => UIO(false))(
                HttpMiddleware.addHeader("TestHeader", "left"),
                HttpMiddleware.addHeader("TestHeader", "right"),
              ))(Request())
            assertM(app.map(_.getHeaderValue("TestHeader")))(isSome(equalTo("right")))
          }
      } +
      suite("ifThenElse") {
        testM("if the condition is true take first") {
          val app =
            (HttpApp.ok @@ ifThenElse((_, _, _) => true)(
              HttpMiddleware.addHeader("TestHeader", "left"),
              HttpMiddleware.addHeader("TestHeader", "right"),
            ))(Request())
          assertM(app.map(_.getHeaderValue("TestHeader")))(isSome(equalTo("left")))
        } +
          testM("if the condition is false take 2nd") {
            val app =
              (HttpApp.ok @@ ifThenElse((_, _, _) => false)(
                HttpMiddleware.addHeader("TestHeader", "left"),
                HttpMiddleware.addHeader("TestHeader", "right"),
              ))(Request())
            assertM(app.map(_.getHeaderValue("TestHeader")))(isSome(equalTo("right")))
          }
      } +
      suite("whenM") {
        testM("if the condition is true apply middleware") {
          val app =
            (HttpApp.ok @@ whenM((_, _, _) => UIO(true))(
              HttpMiddleware.addHeader("TestHeader", "TestValue"),
            ))(Request())
          assertM(app.map(_.getHeaderValue("TestHeader")))(isSome(equalTo("TestValue")))
        } +
          testM("if the condition is false don't apply any middleware") {
            val app =
              (HttpApp.ok @@ whenM((_, _, _) => UIO(false))(
                HttpMiddleware.addHeader("TestHeader", "TestValue"),
              ))(Request())
            assertM(app.map(_.getHeaderValue("TestHeader")))(isNone)
          }
      } +
      suite("when") {
        testM("if the condition is true apple middleware") {
          val app =
            (HttpApp.ok @@ when((_, _, _) => true)(
              HttpMiddleware.addHeader("TestHeader", "TestValue"),
            ))(Request())
          assertM(app.map(_.getHeaderValue("TestHeader")))(isSome(equalTo("TestValue")))
        } +
          testM("if the condition is false don't apply the middleware") {
            val app =
              (HttpApp.ok @@ when((_, _, _) => false)(
                HttpMiddleware.addHeader("TestHeader", "TestValue"),
              ))(Request())
            assertM(app.map(_.getHeaderValue("TestHeader")))(isNone)
          }
      }

  }
}
