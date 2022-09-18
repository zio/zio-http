package example

import zio.http._
import zio.http.model.Method
import zio.{Scope, ZIO, ZIOAppDefault}

object ClientServer extends ZIOAppDefault {

  val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "hello" =>
      ZIO.succeed(Response.text("hello"))

    case Method.GET -> !! =>
      val url = "http://localhost:8080/hello"
      Client.request(url)
  }

  val run = {
    Server.serve(app).provide(Server.default, Client.default, Scope.default).exitCode
  }
}
