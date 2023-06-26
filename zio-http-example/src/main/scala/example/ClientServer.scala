package example

import zio.{Scope, ZIO, ZIOAppDefault}

import zio.http._

object ClientServer extends ZIOAppDefault {
  val url = URL.decode("http://localhost:8080/hello").toOption.get

  val app = Route(
    Method.GET / "hello" -> handler(Response.text("hello")),
    Method.GET / "" -> handler(ZClient.request(Request.get(url)))
  ).toApp

  val run = {
    Server.serve(app.withDefaultErrorResponse).provide(Server.default, Client.default, Scope.default).exitCode
  }
}
