package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientFlexibleRouteHandlers extends ZIOSpecDefault {
  def spec = test("handler can respond dynamically to path parameters") {
    for {
      client <- ZIO.service[Client]
      // Instead of exact mappings, use a handler that can respond to different requests
      _ <- TestClient.addRoute {
        Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
          ZIO.succeed(Response.json(s"""{"id": $id, "name": "User $id"}"""))
        }
      }
      // Now any GET /users/{id} request will be handled
      response1 <- client(Request.get(URL.root / "users" / "1"))
      body1 <- response1.body.asString
      response2 <- client(Request.get(URL.root / "users" / "99"))
      body2 <- response2.body.asString
    } yield assertTrue(
      body1.contains("User 1"),
      body2.contains("User 99")
    )
  }.provide(TestClient.layer, Scope.default)
}
