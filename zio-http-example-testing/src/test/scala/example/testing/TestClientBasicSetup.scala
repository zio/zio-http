package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientBasicSetup extends ZIOSpecDefault {
  def spec = test("mock external API call") {
    for {
      client <- ZIO.service[Client]

      // Configure the mock: when this request is made, return this response
      _ <- TestClient.addRequestResponse(
        Request.get(URL.root / "users" / "1"),
        Response.json("""{"id": 1, "name": "Alice", "email": "alice@example.com"}""")
      )

      // Your code calls the client with the request
      response <- client(Request.get(URL.root / "users" / "1"))
      body <- response.body.asString
    } yield assertTrue(body.contains("Alice"))
  }.provide(TestClient.layer, Scope.default)
}
