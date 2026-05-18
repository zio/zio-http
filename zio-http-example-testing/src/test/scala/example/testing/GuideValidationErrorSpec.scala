package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Testing HTTP Applications — Testing Error Messages
 *
 * Demonstrates how to test that error responses include helpful messages
 * by examining response headers and status codes together.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideValidationErrorSpec"
 */
object GuideValidationErrorSpec extends ZIOSpecDefault {
  def spec = test("error response includes helpful message") {
    for {
      client <- ZIO.service[Client]
      port   <- ZIO.serviceWithZIO[Server](_.port)
      _ <- TestServer.addRoute {
        Method.POST / "login" -> handler { (req: Request) =>
          req.body.asString
            .map { body =>
              if (body.isEmpty)
                Response.status(Status.BadRequest)
                  .addHeader("X-Error", "Username and password required")
              else
                Response.ok
            }
            .catchAll { _ =>
              ZIO.succeed(Response.status(Status.BadRequest))
            }
        }
      }
      response <- client(Request.post(URL.root.port(port) / "login", Body.empty))
      errorMsg = response.headers.get("X-Error")
    } yield assertTrue(
      response.status == Status.BadRequest,
      errorMsg.exists(_.contains("required"))
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
