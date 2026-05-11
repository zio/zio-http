package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientIntegration extends ZIOSpecDefault {
  def spec = test("server calls external service via mocked client") {
    for {
      serverClient <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      // Mock the external service
      _ <- TestClient.addRoute {
        Method.GET / "external" -> handler(Response.json("""{"data": "from external"}"""))
      }

      // Define server route that calls external service
      _ <- TestServer.addRoute {
        Method.GET / "api" -> handler { (_: Request) =>
          serverClient(Request.get(URL.root / "external"))
            .flatMap(_.body.asString)
            .map(data => Response.text(s"Got: $data"))
            .catchAll { _ =>
              ZIO.succeed(Response.status(Status.InternalServerError))
            }
        }
      }

      // Test the server route
      response <- serverClient(Request.get(URL.root.port(port) / "api"))
      body <- response.body.asString
    } yield assertTrue(body.contains("external"))
  }.provide(TestServer.default, TestClient.layer, Scope.default)
}
