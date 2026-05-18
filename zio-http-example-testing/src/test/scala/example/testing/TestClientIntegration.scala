package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientIntegration extends ZIOSpecDefault {
  def spec = test("client calls external service via mocked client") {
    for {
      client <- ZIO.service[Client]

      // Mock the external service response
      _ <- TestClient.addRoute {
        Method.GET / "external" -> handler(Response.json("""{"data": "from external"}"""))
      }

      // Call the external service through the mocked client
      response <- client(Request.get(URL.root / "external"))
      body     <- response.body.asString
    } yield assertTrue(body.contains("from external"))
  }.provide(TestClient.layer, Scope.default)
}
