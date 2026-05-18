package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Pattern 2: TestClient with Fallback Handler
 *
 * Demonstrates how to use TestClient.setFallbackHandler to track unexpected requests
 * and verify that your code only makes expected HTTP calls.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideFallbackHandlerSpec"
 */
object GuideFallbackHandlerSpec extends ZIOSpecDefault {
  def spec = test("verify expected requests are made") {
    for {
      client <- ZIO.service[Client]
      // Track unexpected requests
      unexpectedCalls <- Ref.make[List[Request]](Nil)
      _ <- TestClient.setFallbackHandler { req =>
        unexpectedCalls.update(_ :+ req).as(Response.notFound)
      }
      // Configure expected route
      _ <- TestClient.addRoute {
        Method.GET / "expected" -> handler(Response.ok)
      }
      // Your code makes some requests
      _ <- client(Request.get(URL.root / "expected"))
      _ <- client(Request.get(URL.root / "unexpected"))
      // Verify what was called
      unexpectedRequests <- unexpectedCalls.get
    } yield assertTrue(
      unexpectedRequests.length == 1,
      unexpectedRequests.head.url.path.encode == "/unexpected"
    )
  }.provide(TestClient.layer, Scope.default)
}
