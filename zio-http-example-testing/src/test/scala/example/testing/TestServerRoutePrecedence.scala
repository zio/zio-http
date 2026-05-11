package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerRoutePrecedence extends ZIOSpecDefault {
  def spec = test("specific route takes precedence") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoutes {
        Routes(
          // Specific route checked first
          Method.GET / "items" / int("id") -> handler { (id: Int, _: Request) =>
            ZIO.succeed(Response.json(s"""{"type": "specific"}"""))
          },
          // Fallback route
          Method.GET / trailing -> handler { (_: Request) =>
            ZIO.succeed(Response.json(s"""{"type": "fallback"}"""))
          },
        )
      }

      // Matches specific route
      specificResp <- client(Request.get(URL.root.port(port) / "items" / "42"))
      specificBody <- specificResp.body.asString

      // Matches fallback
      fallbackResp <- client(Request.get(URL.root.port(port) / "other"))
      fallbackBody <- fallbackResp.body.asString
    } yield assertTrue(
      specificBody.contains("specific"),
      fallbackBody.contains("fallback")
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
