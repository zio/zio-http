package example

import zhttp.http._
import zhttp.service.{Client, Server}
import zio.{ZIO, ZIOAppDefault}

object ClientServer extends ZIOAppDefault {

  val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "hello" =>
      ZIO.succeed(Response.text("hello"))

    case Method.GET -> !! =>
      val url = "http://localhost:8080/hello"
      Client.request(url)
  }

  val run = {
    Server.start(8080, app).exitCode
  }
}
