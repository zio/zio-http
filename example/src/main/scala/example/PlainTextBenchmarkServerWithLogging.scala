package example

import io.netty.util.AsciiString
import zhttp.http.{Http, Middleware, Response}
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._
import zio.logging.{LogFormat, LogLevel, Logging, log}

object PlainTextBenchmarkServerWithLogging extends App {

  val logEnv =
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat(),
    ) >>> Logging.withRootLoggerName("zio-http")

  private val message: String = "Hello, World!"

  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val frozenResponse = Response
    .text(message)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    frozenResponse
      .flatMap(server(_).make.useForever)
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(8) ++ logEnv)
      .exitCode
  }

  private def app(response: Response) = Http.response(response)

  val logger = Middleware.log { case (data, logLevel: zhttp.http.LogLevel) =>
    logLevel match {
      case zhttp.http.LogLevel.Error =>
        log.error(data)

      case zhttp.http.LogLevel.Info =>
        log.info(data)
    }

  }

  private def server(response: Response) =
    Server.app(app(response) @@ logger) ++
      Server.port(8080) ++
      Server.error(_ => UIO.unit) ++
      Server.keepAlive ++
      Server.disableLeakDetection ++
      Server.consolidateFlush
}
