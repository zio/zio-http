package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerMultipleRoutes extends ZIOSpecDefault {
  def spec = suite("HTTP methods")(
    test("GET request") {
      for {
        client <- ZIO.service[Client]
        port <- ZIO.serviceWithZIO[Server](_.port)

        _ <- TestServer.addRoute {
          Method.GET / "data" -> Handler.text("GET data")
        }

        response <- client(Request.get(URL.root.port(port) / "data"))
        body <- response.body.asString
      } yield assertTrue(body == "GET data")
    },
    test("POST request") {
      for {
        client <- ZIO.service[Client]
        port <- ZIO.serviceWithZIO[Server](_.port)

        _ <- TestServer.addRoute {
          Method.POST / "data" -> Handler.text("POST data")
        }

        response <- client(Request.post(URL.root.port(port) / "data", Body.empty))
        body <- response.body.asString
      } yield assertTrue(body == "POST data")
    },
  ).provide(TestServer.default, Client.default, Scope.default)
}
