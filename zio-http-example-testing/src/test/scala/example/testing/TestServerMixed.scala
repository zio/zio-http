package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerMixed extends ZIOSpecDefault {
  def spec = test("server calling external service") {
    for {
      // TestClient mocks external dependencies
      externalClient <- ZIO.service[Client]
      _ <- TestClient.addRequestResponse(
        Request.get(URL.root / "external"),
        Response.json("""{"data": "external"}""")
      )

      // TestServer provides the server being tested
      serverClient <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.GET / "api" -> handler { (_: Request) =>
          externalClient(Request.get(URL.root / "external"))
            .flatMap(_.body.asString)
            .map(data => Response.text(s"Got: $data"))
            .catchAll { _ =>
              ZIO.succeed(Response.status(Status.InternalServerError))
            }
        }
      }

      response <- serverClient(Request.get(URL.root.port(port) / "api"))
      body <- response.body.asString
    } yield assertTrue(body.contains("external"))
  }.provide(TestServer.default, TestClient.layer, Scope.default)
}
