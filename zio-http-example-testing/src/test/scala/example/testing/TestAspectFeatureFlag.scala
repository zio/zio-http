package example.testing

import zio._
import zio.http._
import zio.test._

object TestAspectFeatureFlag extends ZIOSpecDefault {
  def spec = (test("new feature is disabled in prod") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.GET / "new-feature" -> handler { (_: Request) =>
          if (Mode.isProd)
            ZIO.succeed(Response.status(Status.NotFound))
          else
            ZIO.succeed(Response.text("Feature enabled"))
        }
      }

      response <- client(Request.get(URL.root.port(port) / "new-feature"))
    } yield assertTrue(
      response.status == Status.NotFound,
      Mode.isProd
    )
  }.provide(TestServer.default, Client.default, Scope.default)) @@ HttpTestAspect.prodMode
}
