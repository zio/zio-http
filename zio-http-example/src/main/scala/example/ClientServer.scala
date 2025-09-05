//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

import zio.http._

object ClientServer extends ZIOAppDefault {
  private val url = URL.decode("http://localhost:8080/hello").toOption.get

  private val app =
    Routes(
      Method.GET / "hello" -> handler(Response.text("hello")),
      Method.GET / ""      -> handler(ZClient.batched(Request.get(url))),
    ).sandbox

  override val run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    Server.serve(app).provide(Server.default, Client.default)
}
