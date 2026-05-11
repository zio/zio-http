package example.testing

import zio._
import zio.http._
import zio.test._

object TestingErrorPaths extends ZIOSpecDefault {
  def spec = test("returns 401 when authorization missing") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.GET / "protected" -> handler { (req: Request) =>
          if (req.headers.contains("Authorization"))
            ZIO.succeed(Response.ok)
          else
            ZIO.succeed(
              Response.status(Status.Unauthorized)
                .addHeader("WWW-Authenticate", "Bearer realm=\"api\"")
            )
        }
      }

      // Without auth → 401
      noAuthResp <- client(Request.get(URL.root.port(port) / "protected"))
      // With auth → 200
      withAuthResp <- client(
        Request.get(URL.root.port(port) / "protected")
          .addHeader("Authorization", "Bearer token123")
      )
    } yield assertTrue(
      noAuthResp.status == Status.Unauthorized,
      withAuthResp.status == Status.Ok
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
