package example

import scala.annotation.nowarn

import zio._

import zio.http._

object ServeOnAnyOpenPort extends ZIOAppDefault {
  val httpApp =
    Routes(
      Method.GET / "hello" -> handler(Response.text("Hello, World!")),
    )

  @nowarn("msg=dead code")
  val app = for {
    port <- Server.install(httpApp)
    _    <- ZIO.log(s"server started on port $port")
    _    <- ZIO.never
  } yield ()

  def run = app.provide(Server.defaultWith(_.onAnyOpenPort))
}
