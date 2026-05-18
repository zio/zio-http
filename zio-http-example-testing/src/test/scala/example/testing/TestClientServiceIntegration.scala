package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientServiceIntegration extends ZIOSpecDefault {
  def spec = test("handler calls external API correctly") {
    for {
      client <- ZIO.service[Client]

      // Mock the external payment service
      _ <- TestClient.addRoute {
        Method.POST / "payment" / "charge" -> handler { (req: Request) =>
          req.body.asString
            .map { body =>
              if (body.contains("100"))
                Response.json("""{"transaction_id": "tx_123", "status": "approved"}""")
              else
                Response.status(Status.BadRequest)
            }
            .catchAll { _ =>
              ZIO.succeed(Response.status(Status.BadRequest))
            }
        }
      }

      // Your code calls the payment service
      response <- client(Request.post(
        URL.root / "payment" / "charge",
        Body.fromString("""{"amount": 100}""")
      ))
      body <- response.body.asString
    } yield assertTrue(body.contains("tx_123"))
  }.provide(TestClient.layer, Scope.default)
}
