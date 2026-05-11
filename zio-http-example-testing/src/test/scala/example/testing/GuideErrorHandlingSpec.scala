package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Testing Error Scenarios
 *
 * Demonstrates how to test HTTP error responses including status codes
 * like 401 (Unauthorized) and 400 (Bad Request).
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideErrorHandlingSpec"
 */
object GuideErrorHandlingSpec extends ZIOSpecDefault {
  def spec = suite("error responses")(
    test("unauthorized when missing auth header") {
      for {
        client <- ZIO.service[Client]
        port   <- ZIO.serviceWithZIO[Server](_.port)
        // Configure a protected endpoint
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
        response <- client(Request.get(URL.root.port(port) / "protected"))
      } yield assertTrue(response.status == Status.Unauthorized)
    },
    test("bad request when input is invalid") {
      for {
        client <- ZIO.service[Client]
        port   <- ZIO.serviceWithZIO[Server](_.port)
        _ <- TestServer.addRoute {
          Method.POST / "users" -> handler { (_: Request) =>
            ZIO.succeed(
              Response.status(Status.BadRequest)
                .addHeader("X-Error", "Missing required field")
            )
          }
        }
        response <- client(Request.post(URL.root.port(port) / "users", Body.empty))
      } yield assertTrue(response.status == Status.BadRequest)
    },
  ).provide(TestServer.default, Client.default, Scope.default)
}
