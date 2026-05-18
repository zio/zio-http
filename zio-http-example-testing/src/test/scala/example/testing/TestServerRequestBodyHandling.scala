package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerRequestBodyHandling extends ZIOSpecDefault {
  def spec = test("handler reads request body") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.POST / "echo" -> handler { (req: Request) =>
          req.body.asString
            .map { body =>
              Response.text(s"Echo: $body")
            }
            .catchAll { _ =>
              ZIO.succeed(Response.status(Status.BadRequest))
            }
        }
      }

      response <- client(Request.post(URL.root.port(port) / "echo", Body.fromString("Hello")))
      body <- response.body.asString
    } yield assertTrue(body == "Echo: Hello")
  }.provide(TestServer.default, Client.default, Scope.default)
}
