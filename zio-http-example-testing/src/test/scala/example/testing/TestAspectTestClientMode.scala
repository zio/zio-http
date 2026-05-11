package example.testing

import zio._
import zio.http._
import zio.test._

object TestAspectTestClientMode extends ZIOSpecDefault {
  def spec = (test("uses mock service in dev mode") {
    for {
      client <- ZIO.service[Client]

      // Configure mock external service
      _ <- TestClient.addRoute {
        Method.GET / "external" -> handler(Response.json("""{"mock": true}"""))
      }

      // Call it
      response <- client(Request.get(URL.root / "external"))
      body <- response.body.asString
    } yield assertTrue(body.contains("mock"))
  }.provide(TestClient.layer, Scope.default)) @@ HttpTestAspect.devMode
}
