package example

import zio._

import zio.config.typesafe._

import zio.http._

object ServerConfigurationExample extends ZIOAppDefault {
  val httpApp = Routes(
    Method.GET / "hello" -> handler(Response.text("Hello, World!")),
  ).sandbox.toHttpApp

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(ConfigProvider.fromResourcePath())

  def run = Server.serve(httpApp).provide(Server.configured())
}
