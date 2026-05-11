package example.testing

import zio._
import zio.http._
import zio.test._

object TestAspectProdMode extends ZIOSpecDefault {
  def spec = (test("request validation is stricter in prod mode") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.POST / "data" -> handler { (req: Request) =>
          val lenientValidation = Mode.isDev
          req.body.asString
            .map { body =>
              if (body.isEmpty && !lenientValidation)
                Response.status(Status.BadRequest)
              else
                Response.ok
            }
            .catchAll { _ =>
              ZIO.succeed(Response.status(Status.BadRequest))
            }
        }
      }

      // Send empty body
      response <- client(Request.post(URL.root.port(port) / "data", Body.empty))
    } yield assertTrue(
      response.status == Status.BadRequest,
      Mode.isProd
    )
  }.provide(TestServer.default, Client.default, Scope.default)) @@ HttpTestAspect.prodMode
}
