package example

import zio._

import zio.config.typesafe._

import zio.http._

object ServerConfigurationExample extends ZIOAppDefault {
  val routes = Routes(
    Method.GET / "hello" -> handler(Response.text("Hello, World!")),
  ).sandbox

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(ConfigProvider.fromResourcePath())

  def run = Server.serve(routes).provide(Server.configured())
}
