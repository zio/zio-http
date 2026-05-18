package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerHeaderHandling extends ZIOSpecDefault {
  def spec = test("handles authentication header") {
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

      // Without auth header
      noAuthResp <- client(Request.get(URL.root.port(port) / "protected"))

      // With auth header
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
