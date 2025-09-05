//> using dep "dev.zio::zio-http:3.4.1"

package example

import scala.annotation.nowarn

import zio._

import zio.http._

object ServeOnAnyOpenPort extends ZIOAppDefault {
  val routes =
    Routes(
      Method.GET / "hello" -> handler(Response.text("Hello, World!")),
    )

  @nowarn("msg=dead code")
  val app = for {
    port <- Server.install(routes)
    _    <- ZIO.log(s"server started on port $port")
    _    <- ZIO.never
  } yield ()

  def run = app.provide(Server.defaultWith(_.onAnyOpenPort))
}
