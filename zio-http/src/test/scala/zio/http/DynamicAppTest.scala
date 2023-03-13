package zio.http

import zio._
import zio.test._

import zio.http.model._
import zio.http.netty.client.NettyClientDriver

object DynamicAppTest extends ZIOSpecDefault {

  val httpApp1: App[Any] = Http
    .collect[Request] { case Method.GET -> !! / "good" =>
      Response.ok
    }
    .withDefaultErrorResponse

  val httpApp2: App[Any] = Http
    .collect[Request] { case Method.GET -> !! / "better" =>
      Response.status(Status.Created)
    }
    .withDefaultErrorResponse

  val layer =
    ZLayer.make[Client & Server](
      ClientConfig.default,
      NettyClientDriver.fromConfig,
      Client.live,
      ServerConfig.live,
      Server.live,
    )

  def spec = suite("Server")(
    test("Should allow dynamic changes to the installed app") {
      for {
        port            <- Server.install(httpApp1)
        okResponse      <- Client.request(s"http://localhost:$port/good")
        _               <- Server.install(httpApp2)
        createdResponse <- Client.request(s"http://localhost:$port/better")
      } yield assertTrue(
        okResponse.status == Status.Ok &&
          createdResponse.status == Status.Created,
      ) // fails here because the response is Status.NotFound
    }.provideLayer(layer),
  )
}
