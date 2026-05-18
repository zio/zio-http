package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Pattern 2: TestClient - Mock HTTP Dependencies
 *
 * Demonstrates how to mock a simple API endpoint using TestClient.addRequestResponse,
 * which returns a specific response for an exact request.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideTestClientBasicSpec"
 */
object GuideTestClientBasicSpec extends ZIOSpecDefault {
  def spec = test("mock a simple API endpoint") {
    for {
      client <- ZIO.service[Client]
      // Configure the mock: when this exact request is made, return this response
      _ <- TestClient.addRequestResponse(
        Request.get(URL.root / "users" / "1"),
        Response.json("""{"id": 1, "name": "Alice", "email": "alice@example.com"}""")
      )
      // Your code calls the client with the request
      response <- client(Request.get(URL.root / "users" / "1"))
      body     <- response.body.asString
    } yield assertTrue(body.contains("Alice"))
  }.provide(TestClient.layer, Scope.default)
}
