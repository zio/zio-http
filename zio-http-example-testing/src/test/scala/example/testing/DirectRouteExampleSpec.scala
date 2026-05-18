package example.testing

import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Pattern 1: Direct Route Testing
 *
 * Demonstrates how to test routes directly without any server infrastructure
 * by calling runZIO on a route with a request and asserting on the response.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.DirectRouteExampleSpec"
 */
object DirectRouteExampleSpec extends ZIOSpecDefault {
  def spec = test("handler returns OK status") {
    // Create a simple handler that always returns OK
    val handler = Handler.ok.toRoutes

    // Call the route directly with a request
    val request = Request.get(URL(Path.root))

    // Assert the response status
    for {
      response <- handler.runZIO(request)
    } yield assertTrue(response.status == Status.Ok)
  }
}
