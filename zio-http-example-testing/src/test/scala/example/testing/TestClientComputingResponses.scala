package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientComputingResponses extends ZIOSpecDefault {
  def spec = test("handler can perform computation on request") {
    for {
      client <- ZIO.service[Client]
      _ <- TestClient.addRoutes {
        Routes(
          Method.POST / "calculate" -> handler { (req: Request) =>
            // Handler can read the request and compute response
            req.body.asString
              .map { body =>
                val number = body.toIntOption.getOrElse(0)
                Response.json(s"""{"result": ${number * 2}}""")
              }
              .catchAll { _ =>
                ZIO.succeed(Response.status(Status.BadRequest))
              }
          }
        )
      }
      response <- client(Request.post(URL.root / "calculate", Body.fromString("21")))
      body <- response.body.asString
    } yield assertTrue(body.contains("42"))
  }.provide(TestClient.layer, Scope.default)
}
