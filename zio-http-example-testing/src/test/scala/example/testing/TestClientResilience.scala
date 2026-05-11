package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientResilience extends ZIOSpecDefault {
  def spec = test("code handles external service timeout") {
    for {
      client <- ZIO.service[Client]

      // Mock the external service as unavailable
      _ <- TestClient.addRoute {
        Method.GET / "external" -> handler(Response.status(Status.ServiceUnavailable))
      }

      // Your code makes the request
      response <- client(Request.get(URL.root / "external"))
    } yield assertTrue(response.status == Status.ServiceUnavailable)
  }.provide(TestClient.layer, Scope.default)
}
