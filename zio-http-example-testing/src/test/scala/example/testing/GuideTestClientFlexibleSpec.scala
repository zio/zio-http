package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Pattern 2: TestClient with Dynamic Handler
 *
 * Demonstrates how to use TestClient.addRoute with a flexible handler that
 * processes path parameters dynamically instead of exact request matching.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideTestClientFlexibleSpec"
 */
object GuideTestClientFlexibleSpec extends ZIOSpecDefault {
  def spec = test("mock with dynamic handler") {
    for {
      client <- ZIO.service[Client]
      // Handler receives the path parameter and can use it
      _ <- TestClient.addRoute {
        Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
          ZIO.succeed(Response.json(s"""{"id": $id, "name": "User $id"}"""))
        }
      }
      // Now any GET /users/{id} request will be handled by this handler
      response1 <- client(Request.get(URL.root / "users" / "1"))
      body1     <- response1.body.asString
      response2 <- client(Request.get(URL.root / "users" / "99"))
      body2     <- response2.body.asString
    } yield assertTrue(
      body1.contains("User 1"),
      body2.contains("User 99")
    )
  }.provide(TestClient.layer, Scope.default)
}
