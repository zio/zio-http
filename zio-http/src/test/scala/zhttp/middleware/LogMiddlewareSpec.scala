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
        console.putStrLn(s"ERROR: $data")
      case LogLevel.Info  =>
        console.putStrLn(s"INFO: $data")
    }
  }

  override def spec = suite("LogMiddleware") {
    suite("log") {
      testM("GET request should generate proper logging") {
        val program = run(app @@ logger, URL(!! / "plaintext")) *> TestConsole.output
        assertM(program.map(_.mkString))(
          containsString(
            "INFO: Request: \n Url: /plaintext\n Method: GET\n Headers: \n \n Response: \n  Status: OK \n  Duration: 0ms\n  Headers: ",
          ),
        )
      } +
        testM("GET request should generate proper logging when server responds with 500") {
          val program = run(app @@ logger, URL(!! / "fail")) *> TestConsole.output
          assertM(program.map(_.mkString))(
            containsString(
              "ERROR: Request: \n Url: /fail\n Method: GET\n Headers: \n \n Response: \n  Status: INTERNAL_SERVER_ERROR \n  Duration: 0ms\n  Headers: ",
            ),
          )
        } +
        testM("GET request should generate proper logging when server responds with 502") {
          val program = run(app @@ logger, URL(!! / "bad_gateway")) *> TestConsole.output
          assertM(program.map(_.mkString))(
            containsString(
              "ERROR: Request: \n Url: /bad_gateway\n Method: GET\n Headers: \n \n Response: \n  Status: BAD_GATEWAY \n  Duration: 0ms\n  Headers: ",
            ),
          )
        }
    }
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "plaintext"   =>
      ZIO.succeed(Response.ok).delay(10 seconds)
    case Method.GET -> !! / "fail"        =>
      ZIO.fail(new RuntimeException("Error"))
    case Method.GET -> !! / "bad_gateway" =>
      ZIO.succeed(Response.status(Status.BAD_GATEWAY))
  }

  private def run[R, E](app: HttpApp[R, E], url: URL): ZIO[TestClock with R, Option[E], Response] = {
    for {
      fib <- app { Request(url = url) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
