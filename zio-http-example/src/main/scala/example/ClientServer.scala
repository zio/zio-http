package example

import zio.{Scope, ZIO, ZIOAppDefault}

import zio.http._

object ClientServer extends ZIOAppDefault {
  val url = URL.decode("http://localhost:8080/hello").toOption.get

  val app = Routes(
    Method.GET / "hello" -> handler(Response.text("hello")),
    Method.GET / ""      -> handler(ZClient.request(Request.get(url))),
  ).ignore.toHttpApp

  val run = 
    Server.serve(app).provide(Server.default, Client.default, Scope.default).exitCode
}
