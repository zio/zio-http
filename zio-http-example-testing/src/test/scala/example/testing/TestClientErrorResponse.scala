package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientErrorResponse extends ZIOSpecDefault {
  def spec = test("mock error responses from external service") {
    for {
      client <- ZIO.service[Client]
      _ <- TestClient.addRoute {
        Method.GET / "external" / "unavailable" -> handler(Response.status(Status.ServiceUnavailable))
      }
      _ <- TestClient.addRoute {
        Method.GET / "protected" -> handler {
          Response.status(Status.Unauthorized)
            .addHeader("WWW-Authenticate", "Bearer realm=\"api\"")
        }
      }
      unavailResp <- client(Request.get(URL.root / "external" / "unavailable"))
      unauthorizedResp <- client(Request.get(URL.root / "protected"))
    } yield assertTrue(
      unavailResp.status == Status.ServiceUnavailable,
      unauthorizedResp.status == Status.Unauthorized
    )
  }.provide(TestClient.layer, Scope.default)
}
