package example

import zhttp.http._
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.server.ServerChannelFactory
import zio.{App, ExitCode, URIO}

object HelloWorld extends App {

  def server = Server.app(HttpApp.ok) ++ Server.port(8090) ++ Server.keepAlive ++ Server.disableLeakDetection

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    server.make.useForever.provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto()).exitCode
}
