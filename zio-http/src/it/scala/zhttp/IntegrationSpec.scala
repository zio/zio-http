package zhttp

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio._
import zio.test.DefaultRunnableSpec

object IntegrationSpec extends DefaultRunnableSpec {
  implicit val addr = "localhost"
  implicit val port = 80
  def env           = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  def app = HttpApp.collectM {
    case Method.GET -> !!  => ZIO.succeed(Response.ok)
    case Method.POST -> !! => ZIO.succeed(Response.status(Status.CREATED))
  }

  def spec = suite("IntegrationSpec")(
    HttpSpec.testSuite,
  ) provideCustomLayer env

  Runtime.default.unsafeRun(Server.start(port, app).forkDaemon)
}
