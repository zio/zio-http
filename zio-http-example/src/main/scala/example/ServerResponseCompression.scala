package example

import zio._

import zio.http._

object ServerResponseCompression extends ZIOAppDefault {
  val httpApp = Routes(
    Method.GET / "hello" -> handler(Response.text("Hello, World!")),
  ).sandbox.toHttpApp

  val config = ZLayer.succeed(
    Server.Config.default.copy(
      responseCompression = Some(Server.Config.ResponseCompressionConfig.default),
    ),
  )

  def run = Server.serve(httpApp).provide(Server.live, config)
}
