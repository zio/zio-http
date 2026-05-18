package example.testing

import zio._
import zio.http._
import zio.test._

object MockingExternalDependencies extends ZIOSpecDefault {
  def spec = test("calls payment processor correctly") {
    for {
      client <- ZIO.service[Client]
      // Mock the payment processor
      _ <- TestClient.addRequestResponse(
        Request.post(URL.root / "charge", Body.fromString("""{"amount": 100}""")),
        Response.json("""{"transaction_id": "tx_123", "status": "approved"}""")
      )
      // Your code calls the mocked client
      response <- client(Request.post(URL.root / "charge", Body.fromString("""{"amount": 100}""")))
      body <- response.body.asString
    } yield assertTrue(body.contains("tx_123"))
  }.provide(TestClient.layer, Scope.default)
}
