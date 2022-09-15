package example

import zhttp.http._
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio.http.service.Client
import zio.{App, ZIO}

object ClientServer extends App {

  val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "hello" =>
      ZIO.succeed(Response.text("hello"))

    case Method.GET -> !! =>
      val url = "http://localhost:8080/hello"
      Client.request(url)
  }

  override def run(args: List[String]) = {
    val clientLayers = ChannelFactory.auto ++ EventLoopGroup.auto()
    Server.start(8080, app).provideLayer(clientLayers).exitCode
  }
}
