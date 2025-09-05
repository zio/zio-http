//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object ServerResponseCompression extends ZIOAppDefault {
  val routes = Routes(
    Method.GET / "hello" -> handler(Response.text("Hello, World!")),
  ).sandbox

  val config = ZLayer.succeed(
    Server.Config.default.copy(
      responseCompression = Some(Server.Config.ResponseCompressionConfig.default),
    ),
  )

  def run = Server.serve(routes).provide(Server.live, config)
}
