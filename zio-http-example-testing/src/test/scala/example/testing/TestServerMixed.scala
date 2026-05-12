package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerMixed extends ZIOSpecDefault {
  def spec = test("server calling external service") {
    for {
      client <- ZIO.service[Client]
      port   <- ZIO.serviceWithZIO[Server](_.port)

      // Add a route simulating the external dependency
      _ <- TestServer.addRoute {
        Method.GET / "external" -> handler { (_: Request) =>
          ZIO.succeed(Response.json("""{"data": "external"}"""))
        }
      }

      // Add the route under test: calls the external dependency then returns a result
      _ <- TestServer.addRoute {
        Method.GET / "api" -> handler { (_: Request) =>
          client(Request.get(URL.root.port(port) / "external"))
            .flatMap(_.body.asString)
            .map(data => Response.text(s"Got: $data"))
            .catchAll { _ =>
              ZIO.succeed(Response.status(Status.InternalServerError))
            }
        }
      }

      response <- client(Request.get(URL.root.port(port) / "api"))
      body     <- response.body.asString
    } yield assertTrue(body.contains("external"))
  }.provide(TestServer.default, Client.default, Scope.default)
}
