package zio.http

import zio._
import zio.test.TestAspect.shrinks
import zio.test._

import zio.http.endpoint.{AuthType, Endpoint}
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

object RoutesPrecedentsSpec extends ZIOSpecDefault {

  trait MyService  {
    def code: UIO[Int]
  }
  object MyService {
    def live(code: Int): ULayer[MyService] = ZLayer.succeed(new MyServiceLive(code))
  }
  final class MyServiceLive(_code: Int) extends MyService {
    def code: UIO[Int] = ZIO.succeed(_code)
  }

  val endpoint: Endpoint[Unit, String, ZNothing, Int, AuthType.None] =
    Endpoint(RoutePattern.POST / "api").in[String].out[Int]

  val api = endpoint.implement(_ => ZIO.serviceWithZIO[MyService](_.code))

  // when adding the same route multiple times to the server, the last one should take precedence
  override def spec: Spec[TestEnvironment & Scope, Any] =
    test("test") {
      check(Gen.fromIterable(List(1, 2, 3, 4, 5))) { code =>
        (
          for {
            client <- ZIO.service[Client]
            port   <- ZIO.serviceWithZIO[Server](_.port)
            url     = URL.root.port(port) / "api"
            request = Request
              .post(url = url, body = Body.fromString(""""this is some input""""))
              .addHeader(Header.Accept(MediaType.application.json))
            _      <- TestServer.addRoutes(api.toRoutes)
            result <- client.batched(request)
            output <- result.body.asString
          } yield assertTrue(output == code.toString)
        ).provideSome[TestServer & Client](
          ZLayer.succeed(new MyServiceLive(code)),
        )
      }.provide(
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        TestServer.layer,
        Client.default,
        NettyDriver.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      )
    } @@ shrinks(0)
}
