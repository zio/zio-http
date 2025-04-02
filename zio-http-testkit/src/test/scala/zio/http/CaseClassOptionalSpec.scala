package zio.http

import zio.json._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{&, Scope, ZIO, ZLayer, ZNothing}

import zio.schema.annotation.optionalField
import zio.schema.{DeriveSchema, Schema}

import zio.http.endpoint.{AuthType, Endpoint}
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

object CaseClassOptionalSpec extends ZIOSpecDefault {
  final case class Params(
    @optionalField
    test1: Option[String],
    @optionalField
    test2: Option[String],
    @optionalField
    test3: Option[Int],
  )
  implicit val params: Schema[Params]                                   = DeriveSchema.gen[Params]
  implicit val jsonCodec: JsonCodec[Params]                             = DeriveJsonCodec.gen[Params]
  val endpoint: Endpoint[Unit, Params, ZNothing, Params, AuthType.None] =
    Endpoint(RoutePattern.GET / "api").query[Params].out[Params]

  val api = endpoint.implement(params => {
    ZIO.succeed(params)
  })

  override def spec: Spec[TestEnvironment & Scope, Any] =
    test("GET with optional query param") {
      for {
        client <- ZIO.service[Client]
        port   <- ZIO.serviceWithZIO[Server](_.port)
        _      <- TestServer.addRoutes(api.toRoutes)
        url     = URL.root.port(port) / "api?test1=a"
        request = Request
          .get(url)
          .addHeader(Header.Accept(MediaType.application.json))
        result <- client.request(request)
        output <- result.body.asString
        decoded = output.fromJson[Params]
      } yield assertTrue(decoded == Right(Params(test1 = Some("a"), test2 = None, test3 = None)))
    }.provideShared(
      Scope.default,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort),
      TestServer.layer,
      Client.default,
      NettyDriver.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

}
