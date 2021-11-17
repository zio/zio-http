package zhttp

import zhttp.http.HttpApp
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio.test.DefaultRunnableSpec

abstract class IntegrationRunnableSpec(val port: Int) extends DefaultRunnableSpec {
  lazy val addr = "localhost"
  def env       = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  def serve[R](app: HttpApp[R, Throwable]) = Server.start(port, app)
}
