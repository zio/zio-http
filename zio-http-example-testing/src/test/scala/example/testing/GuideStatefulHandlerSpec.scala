package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Testing Stateful Handlers
 *
 * Demonstrates how to test handlers that maintain state across multiple requests
 * using Ref to track and verify state changes.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideStatefulHandlerSpec"
 */
object GuideStatefulHandlerSpec extends ZIOSpecDefault {
  def spec = test("handler state persists across requests") {
    for {
      client  <- ZIO.service[Client]
      port    <- ZIO.serviceWithZIO[Server](_.port)
      // Create a Ref to hold a counter
      counter <- Ref.make(0)
      // Configure a route that increments the counter
      _       <- TestServer.addRoute {
        Method.GET / "increment" -> handler { (_: Request) =>
          counter.updateAndGet(_ + 1).map(n => Response.text(s"Count: $n"))
        }
      }
      // Make first request
      resp1 <- client(Request.get(URL.root.port(port) / "increment"))
      body1 <- resp1.body.asString
      // Make second request
      resp2 <- client(Request.get(URL.root.port(port) / "increment"))
      body2 <- resp2.body.asString
      // Make third request
      resp3 <- client(Request.get(URL.root.port(port) / "increment"))
      body3 <- resp3.body.asString
    } yield assertTrue(
      body1 == "Count: 1",
      body2 == "Count: 2",
      body3 == "Count: 3",
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
