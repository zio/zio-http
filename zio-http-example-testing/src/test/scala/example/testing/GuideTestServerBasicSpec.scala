package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Pattern 3: TestServer - Integration Testing
 *
 * Demonstrates how to use TestServer to test routes by making HTTP requests
 * to an in-memory test server using a standard Client.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideTestServerBasicSpec"
 */
object GuideTestServerBasicSpec extends ZIOSpecDefault {
  def spec = test("server responds to requests") {
    for {
      client <- ZIO.service[Client]
      // Get the port the test server is bound to
      port   <- ZIO.serviceWithZIO[Server](_.port)
      // Configure a route
      _      <- TestServer.addRoute {
        Method.GET / "hello" -> Handler.text("Hello, World!")
      }
      // Make a request to localhost:port
      response <- client(Request.get(URL.root.port(port) / "hello"))
      body     <- response.body.asString
    } yield assertTrue(body == "Hello, World!")
  }.provide(TestServer.default, Client.default, Scope.default)
}
