package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio.{App, ExitCode, UIO, URIO}

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object Main extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case req @ Method.GET -> !! / "reqUrl" => Response.text(s"${req.url.host.orNull}")
    case Method.GET -> !! / "resUrl"       =>
      Response.text(s"${URL.fromString("http://yourdomain.com/list/users").getOrElse(null)}")
  }

  private def server =
    Server.app(app) ++
      Server.port(8080) ++
      Server.error(_ => UIO.unit) ++
      Server.disableLeakDetection ++
      Server.consolidateFlush ++
      Server.disableFlowControl

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make.useForever
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(8))
      .exitCode
  }

}
