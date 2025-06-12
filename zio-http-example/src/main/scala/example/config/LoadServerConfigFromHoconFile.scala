//> using dep "dev.zio::zio-http:3.3.1"
//> using dep "dev.zio::zio-config:4.0.4"
//> using dep "dev.zio::zio-config-typesafe:4.0.4"

package example.config

import zio._

import zio.config._
import zio.config.typesafe._

import zio.http._

object LoadServerConfigFromHoconFile extends ZIOAppDefault {
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
      .provide(
        Server.live,
        ZLayer.fromZIO(
          ZIO.config(Server.Config.config.nested("zio.http.server").mapKey(_.replace('-', '_'))),
        ),
      )
  }
}
