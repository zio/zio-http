package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientRouteAccumulation extends ZIOSpecDefault {
  def spec = test("routes accumulate across multiple adds") {
    for {
      client <- ZIO.service[Client]
      // Add first route
      _ <- TestClient.addRoute {
        Method.GET / "first" -> handler(Response.text("First"))
      }
      // Add second route
      _ <- TestClient.addRoute {
        Method.GET / "second" -> handler(Response.text("Second"))
      }
      // Both routes work
      resp1 <- client(Request.get(URL.root / "first"))
      resp2 <- client(Request.get(URL.root / "second"))
      body1 <- resp1.body.asString
      body2 <- resp2.body.asString
    } yield assertTrue(
      body1 == "First",
      body2 == "Second"
    )
  }.provide(TestClient.layer, Scope.default)
}
