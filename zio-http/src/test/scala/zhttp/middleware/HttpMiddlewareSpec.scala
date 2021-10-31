package zhttp.middleware

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

  val app1: HttpApp[Any with Clock, Nothing] = HttpApp.collectM { case Method.GET -> !! / "create" =>
    UIO(Response.status(Status.CREATED)).delay(1 second)
  }

  val app2: HttpApp[Any with Clock, Nothing] = HttpApp.collectM { case Method.GET -> !! / "bad_request" =>
    UIO(Response.status(Status.BAD_REQUEST)).delay(1 second)
  }

  def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

  def runApp1[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app {
        Request(url = URL(!! / "create"))
      }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

  def runApp2[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app {
        Request(url = URL(!! / "bad_request"))
      }.fork
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
      suite("IfThenElse") {
        testM("when condition is true") {
          val cond: (Method, URL, List[Header]) => Boolean = (_, _, _) => true
          val program                                      = run(app @@ ifThenElse(cond)(app1, app2)).map(_.status)
          val program1                                     = runApp1(app @@ ifThenElse(cond)(app1, app2)).map(_.status)
          assertM(program)(equalTo(Status.OK))
          assertM(program1)(equalTo(Status.CREATED))
        } +
          testM("when condition is false") {
            val cond: (Method, URL, List[Header]) => Boolean = (_, _, _) => false
            val program                                      = run(app @@ ifThenElse(cond)(app1, app2)).map(_.status)
            val program2 = runApp2(app @@ ifThenElse(cond)(app1, app2)).map(_.status)
            assertM(program)(equalTo(Status.OK))
            assertM(program2)(equalTo(Status.BAD_REQUEST))
          } +
          testM("when condition is true with effect") {
            val condM: (Method, URL, List[Header]) => UIO[Boolean] = (_, _, _) => UIO(true)
            val program  = run(app @@ ifThenElseM(condM)(app1, app2)).map(_.status)
            val program1 = runApp1(app @@ ifThenElseM(condM)(app1, app2)).map(_.status)
            assertM(program)(equalTo(Status.OK))
            assertM(program1)(equalTo(Status.CREATED))
          } +
          testM("when condition is false with effect") {
            val condM: (Method, URL, List[Header]) => UIO[Boolean] = (_, _, _) => UIO(false)
            val program  = run(app @@ ifThenElseM(condM)(app1, app2)).map(_.status)
            val program2 = runApp2(app @@ ifThenElseM(condM)(app1, app2)).map(_.status)
            assertM(program)(equalTo(Status.OK))
            assertM(program2)(equalTo(Status.BAD_REQUEST))
          }

      }

  }
}
