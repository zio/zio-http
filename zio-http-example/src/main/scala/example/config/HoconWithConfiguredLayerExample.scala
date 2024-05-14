package example.config

import zio._

import zio.config.typesafe._

import zio.http._

object HoconWithConfiguredLayerExample extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(ConfigProvider.fromResourcePath())

  def run = {
    Server
      .install(
        Routes(
          Method.GET / "hello" -> handler(Response.text("Hello, world!")),
        ),
      )
      .flatMap(port => ZIO.debug(s"Sever started on http://localhost:$port") *> ZIO.never)
      .provide(Server.configured())
  }
}
