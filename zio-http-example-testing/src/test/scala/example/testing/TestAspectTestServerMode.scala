package example.testing

import zio._
import zio.http._
import zio.test._

object TestAspectTestServerMode extends ZIOSpecDefault {
  def spec = (test("server returns different response in prod mode") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.GET / "api" -> handler { (_: Request) =>
          if (Mode.isProd)
            ZIO.succeed(Response.text("Production response"))
          else
            ZIO.succeed(Response.text("Development response"))
        }
      }

      response <- client(Request.get(URL.root.port(port) / "api"))
      body <- response.body.asString
    } yield assertTrue(body == "Production response")
  }.provide(TestServer.default, Client.default, Scope.default)) @@ HttpTestAspect.prodMode
}
