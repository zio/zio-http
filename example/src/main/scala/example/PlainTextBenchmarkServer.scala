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
    .freeze

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] { case Method.GET -> !! / "text" =>
    frozenResponse
  }
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(8))
      .exitCode
  }

  private def server =
    Server.app(app) ++
      Server.port(8080) ++
      Server.error(_ => UIO.unit) ++
      Server.disableLeakDetection ++
      Server.consolidateFlush ++
      Server.disableFlowControl

}
