package example.testing

import zio._
import zio.http._
import zio.test._

object TestAspectPreprodMode extends ZIOSpecDefault {
  def spec = (test("preprod mode enables test endpoints") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      // Routes only available in preprod and dev
      testRoutes = if (Mode.isProd) Routes.empty else Routes(
        Method.GET / "test" / "reset" -> handler(Response.ok)
      )

      _ <- TestServer.addRoutes(testRoutes)

      response <- client(Request.get(URL.root.port(port) / "test" / "reset"))
    } yield assertTrue(
      response.status == Status.Ok,
      Mode.isPreprod
    )
  }.provide(TestServer.default, Client.default, Scope.default)) @@ HttpTestAspect.preprodMode
}
