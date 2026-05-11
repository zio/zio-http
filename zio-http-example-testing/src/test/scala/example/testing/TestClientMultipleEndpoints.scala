package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientMultipleEndpoints extends ZIOSpecDefault {
  def spec = test("mock multiple endpoints") {
    for {
      client <- ZIO.service[Client]
      _ <- TestClient.addRequestResponse(
        Request.get(URL.root / "users" / "1"),
        Response.json("""{"id": 1, "name": "Alice"}""")
      )
      _ <- TestClient.addRequestResponse(
        Request.get(URL.root / "users" / "2"),
        Response.json("""{"id": 2, "name": "Bob"}""")
      )
      resp1 <- client(Request.get(URL.root / "users" / "1"))
      resp2 <- client(Request.get(URL.root / "users" / "2"))
      body1 <- resp1.body.asString
      body2 <- resp2.body.asString
    } yield assertTrue(
      body1.contains("Alice"),
      body2.contains("Bob")
    )
  }.provide(TestClient.layer, Scope.default)
}
