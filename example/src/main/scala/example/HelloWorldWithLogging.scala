package example
import zhttp.http._
import zhttp.service.Server
import zio.clock.Clock
import zio.duration._
import zio.logging.{LogFormat, LogLevel, Logging, log}
import zio.{App, ExitCode, URIO, ZIO}

object HelloWorldWithLogging extends App {

  val logEnv =
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat(),
    ) >>> Logging.withRootLoggerName("zio-http")

  val app: HttpApp[Clock, Nothing] = Http.collectZIO[Request] {
    // this will return result instantly
    case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val logger = Middleware.log { case (data, logLevel: zhttp.http.LogLevel) =>
    logLevel match {
      case zhttp.http.LogLevel.Error =>
        log.error(data)

      case zhttp.http.LogLevel.Info =>
        log.info(data)
    }

  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, (app @@ logger).silent).provideCustomLayer(logEnv).exitCode
}
