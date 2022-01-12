package zhttp.middleware

import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.duration._
import zio.test.Assertion.containsString
import zio.test.environment.{TestClock, TestConsole}
import zio.test.{DefaultRunnableSpec, assertM}

object LogMiddlewareSpec extends DefaultRunnableSpec with HttpAppTestExtensions {

  private val logger = Middleware.log { case (data, logLevel: zhttp.http.LogLevel) =>
    logLevel match {
      case LogLevel.Error =>
        console.putStrLn(data)
      case LogLevel.Info  =>
        console.putStrLn(data)
    }
  }

  override def spec = suite("LogMiddleware") {
    suite("log") {
      testM("GET request should generate proper logging") {
        val program = run(app @@ logger) *> TestConsole.output
        assertM(program.map(_.mkString))(
          containsString(
            "Request: \n Url: /plaintext\n Method: GET\n Headers: \n \n Response: \n  Status: OK \n  Duration: 0ms\n  Headers: ",
          ),
        )
      } +
        testM("GET request should generate proper logging when server responds with 500") {
          val program = runFail(app @@ logger) *> TestConsole.output
          assertM(program.map(_.mkString))(
            containsString(
              "Request: \n Url: /plaintext\n Method: GET\n Headers: \n \n Response: \n  Status: OK \n  Duration: 0ms\n  Headers: ",
            ),
          )
        }
    }
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "plaintext" =>
      ZIO.succeed(Response.ok).delay(10 seconds)
    case Method.GET -> !! / "fail"      =>
      ZIO.fail(new RuntimeException("Error"))
  }

  private def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response] = {
    for {
      fib <- app { Request(url = URL(!! / "plaintext")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

  private def runFail[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response] = {
    for {
      fib <- app { Request(url = URL(!! / "fail")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
