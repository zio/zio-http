package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerError extends ZIOSpecDefault {
  def spec = test("returns 404 for missing resource") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.GET / "exists" -> Handler.ok
      }

      // Configured route → 200
      existsResp <- client(Request.get(URL.root.port(port) / "exists"))

      // Unconfigured route → 404
      missingResp <- client(Request.get(URL.root.port(port) / "missing"))
    } yield assertTrue(
      existsResp.status == Status.Ok,
      missingResp.status == Status.NotFound
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
