package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerBasicSetup extends ZIOSpecDefault {
  def spec = test("server responds to requests") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      // Add a route to the test server
      _ <- TestServer.addRoute {
        Method.GET / "hello" -> Handler.text("Hello, World!")
      }

      // Make an HTTP request
      response <- client(Request.get(URL.root.port(port) / "hello"))
      body <- response.body.asString
    } yield assertTrue(body == "Hello, World!")
  }.provide(TestServer.default, Client.default, Scope.default)
}
