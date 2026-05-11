package example.testing

import zio._
import zio.http._
import zio.test._

object HttpTestAspectModeTesting extends ZIOSpecDefault {
  def spec = (test("mode-dependent error handling") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)

      _ <- TestServer.addRoute {
        Method.GET / "error" -> handler { (_: Request) =>
          (ZIO.fail(new Exception("Something went wrong")): ZIO[Any, Throwable, Response])
            .catchAll { err =>
              val message = if (Mode.isDev) err.toString else "Internal Server Error"
              ZIO.succeed(Response.status(Status.InternalServerError).addHeader("X-Error", message))
            }
        }
      }

      response <- client(Request.get(URL.root.port(port) / "error"))
    } yield assertTrue(
      response.status == Status.InternalServerError,
      Mode.isDev
    )
  }.provide(TestServer.default, Client.default, Scope.default)) @@ HttpTestAspect.devMode
}
