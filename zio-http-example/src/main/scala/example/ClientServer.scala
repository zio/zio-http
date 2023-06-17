package example

import zio.{Scope, ZIO, ZIOAppDefault}

import zio.http._

object ClientServer extends ZIOAppDefault {

  val app = Http.collectZIO[Request] {
    case Method.GET -> Root / "hello" =>
      ZIO.succeed(Response.text("hello"))

    case Method.GET -> Root =>
      val url = URL.decode("http://localhost:8080/hello").toOption.get
      ZClient.request(Request.get(url))
  }

  val run = {
    Server.serve(app.defaultErrorResponse).provide(Server.default, Client.default, Scope.default).exitCode
  }
}
