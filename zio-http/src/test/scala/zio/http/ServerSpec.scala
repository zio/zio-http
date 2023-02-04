package zio.http

import zio.http.model.HttpError
import zio.http.model.Method
import zio._
import zio.test._

import ZIOAspect._

object ServerSpec extends ZIOSpecDefault {
  case class Port(port: Int) {
    override def toString = s"$port"
  }

  val http = Http.collectZIO[Request] { case Method.GET -> !! / "hello" =>
    ZIO.succeed(Response.text("Hello")) raceFirst (Clock.instant.as(Response.text("hi")))
  // also fails
  // ZIO.succeed(Response.text("Hello")) raceFirst (ZIO.sleep(1.milli).as(Response.text("hi")))
  }

  val port = ZLayer.scoped(for {
    port <- Server.install(http)
  } yield Port(port))

  def spec: Spec[TestEnvironment with Scope, Any] = suite("ServerSpec")(
    test("it should work") {
      for {
        port <- ZIO.service[Port]
        res  <- Client.request(s"http://localhost:$port/hello")
        body <- res.body.asString
      } yield assertTrue(body == "Hello")
    },
  ).provideShared(Client.default, Server.live, ServerConfig.live(ServerConfig.default.port(0)), port)
}
