package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientExactRequest extends ZIOSpecDefault {
  def spec = test("exact request returns configured response") {
    for {
      client <- ZIO.service[Client]
      _ <- TestClient.addRequestResponse(
        Request.get(URL.root / "status"),
        Response.json("""{"status": "ok"}""")
      )
      response <- client(Request.get(URL.root / "status"))
      body <- response.body.asString
    } yield assertTrue(body.contains("ok"))
  }.provide(TestClient.layer, Scope.default)
}
