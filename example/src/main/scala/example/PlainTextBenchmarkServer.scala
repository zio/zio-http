package example

import io.netty.util.AsciiString
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio.{App, ExitCode, UIO, URIO}

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object Main extends App {

  private val message: String = "Hello, World!"

  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val frozenResponse = Response
    .text(message)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server(frozenResponse).make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(32))
      .exitCode
  }

  private def app(response: Response[Any, Nothing]) = Http.response(response)

  private def server(response: Response[Any, Nothing]) =
    Server.app(app(response)) ++
      Server.port(8080) ++
      Server.error(_ => UIO.unit) ++
      Server.keepAlive ++
      Server.disableLeakDetection ++
      Server.consolidateFlush

}
