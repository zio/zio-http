package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerMode extends ZIOSpecDefault {
  def spec = suite("HTTP modes")(
    test("behaves correctly in dev mode") {
      for {
        client <- ZIO.service[Client]
        port <- ZIO.serviceWithZIO[Server](_.port)
        _ <- TestServer.addRoute {
          Method.GET / "status" -> Handler.text("dev")
        }
        response <- client(Request.get(URL.root.port(port) / "status"))
        body <- response.body.asString
      } yield assertTrue(body == "dev")
    },
  ).provide(TestServer.default, Client.default, Scope.default) @@ HttpTestAspect.devMode
}
