package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Pattern 3: TestServer with Multiple Routes
 *
 * Demonstrates how to use TestServer to test multiple routes working together
 * with route precedence (specific routes before fallback routes).
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideMultiRouteIntegrationSpec"
 */
object GuideMultiRouteIntegrationSpec extends ZIOSpecDefault {
  def spec = test("test interactions between routes") {
    for {
      client <- ZIO.service[Client]
      port   <- ZIO.serviceWithZIO[Server](_.port)
      // Configure both a specific route and a fallback
      _ <- TestServer.addRoutes {
        Routes(
          // Specific route: GET /items/{id}
          Method.GET / "items" / int("id") -> handler { (id: Int, _: Request) =>
            ZIO.succeed(Response.json(s"""{"type": "specific", "id": $id}"""))
          },
          // Fallback: any other GET request
          Method.GET / trailing -> handler { (_: Request) =>
            ZIO.succeed(Response.json("""{"type": "fallback"}"""))
          },
        )
      }
      // Request the specific route
      specificResp <- client(Request.get(URL.root.port(port) / "items" / "42"))
      specificBody <- specificResp.body.asString
      // Request a path that matches the fallback
      fallbackResp <- client(Request.get(URL.root.port(port) / "other"))
      fallbackBody <- fallbackResp.body.asString
    } yield assertTrue(
      specificBody.contains("specific"),
      fallbackBody.contains("fallback"),
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
