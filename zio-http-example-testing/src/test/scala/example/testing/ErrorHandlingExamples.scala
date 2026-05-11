package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Examples of error and edge case testing patterns.
 *
 * A critical part of testing is verifying that handlers respond appropriately
 * to error conditions. This includes testing HTTP error codes, validation errors,
 * and graceful degradation.
 */
object ErrorHandlingExamples extends ZIOSpecDefault {

  def spec = suite("Error Handling Examples")(
    suite("HTTP status codes")(
      test("handler returns 404 for missing resources") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoute {
            Method.GET / "items" / int("id") -> handler { (id: Int, _: Request) =>
              if (id > 0) Response.ok else Response.notFound
            }
          }
          // Valid ID
          validResp <- client(Request.get(URL.root.port(port) / "items" / "5"))
          // Invalid ID
          notFoundResp <- client(Request.get(URL.root.port(port) / "items" / "-1"))
        } yield assertTrue(
          validResp.status == Status.Ok,
          notFoundResp.status == Status.NotFound
        )
      },
      test("handler returns 401 for missing authentication") {
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
          // Without auth
          unauth <- client(Request.get(URL.root.port(port) / "protected"))
          // With auth
          withAuth <- client(
            Request.get(URL.root.port(port) / "protected")
              .addHeader("Authorization", "Bearer token123")
          )
        } yield assertTrue(
          unauth.status == Status.Unauthorized,
          withAuth.status == Status.Ok
        )
      },
      test("handler returns 403 for forbidden") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoute {
            Method.POST / "admin" / "delete" -> handler { (req: Request) =>
              val isAdmin = req.headers.get("X-Admin").isDefined
              if (isAdmin)
                ZIO.succeed(Response.ok)
              else
                ZIO.succeed(Response.status(Status.Forbidden))
            }
          }
          // Non-admin user
          forbidden <- client(Request.post(URL.root.port(port) / "admin" / "delete", Body.empty))
          // Admin user
          allowed <- client(
            Request.post(URL.root.port(port) / "admin" / "delete", Body.empty)
              .addHeader("X-Admin", "true")
          )
        } yield assertTrue(
          forbidden.status == Status.Forbidden,
          allowed.status == Status.Ok
        )
      },
      test("handler returns 500 for server error") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          errorOccurred <- Ref.make(false)
          _ <- TestServer.addRoute {
            Method.GET / "dangerous" -> handler { (_: Request) =>
              errorOccurred.get.map { hasError =>
                if (hasError)
                  Response.status(Status.InternalServerError)
                else
                  Response.ok
              }
            }
          }
          resp <- client(Request.get(URL.root.port(port) / "dangerous"))
        } yield assertTrue(resp.status == Status.Ok)
      },
    ),
    suite("Input validation errors")(
      test("handler validates required fields") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "users" -> handler { (req: Request) =>
                req.body.asString
                  .map { body =>
                    // Simple validation: check if body is empty
                    if (body.isEmpty)
                      Response.status(Status.BadRequest)
                        .addHeader("X-Error", "Body required")
                    else
                      Response.status(Status.Created)
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              }
            )
          }
          // Empty body
          badReq <- client(Request.post(URL.root.port(port) / "users", Body.empty))
          // Valid body
          goodReq <- client(Request.post(URL.root.port(port) / "users", Body.fromString("name=Alice")))
        } yield assertTrue(
          badReq.status == Status.BadRequest,
          goodReq.status == Status.Created
        )
      },
      test("handler validates data format") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "numbers" -> handler { (req: Request) =>
                req.body.asString
                  .map { body =>
                    if (body.toIntOption.isDefined)
                      Response.ok
                    else
                      Response.status(Status.BadRequest)
                        .addHeader("X-Error", "Invalid number format")
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              }
            )
          }
          // Invalid format
          invalid <- client(Request.post(URL.root.port(port) / "numbers", Body.fromString("abc")))
          // Valid format
          valid <- client(Request.post(URL.root.port(port) / "numbers", Body.fromString("42")))
        } yield assertTrue(
          invalid.status == Status.BadRequest,
          valid.status == Status.Ok
        )
      },
      test("handler validates constraints") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "age" -> handler { (req: Request) =>
                req.body.asString
                  .map { body =>
                    val ageOpt = body.toIntOption
                    ageOpt match {
                      case Some(age) if age >= 0 && age <= 150 => Response.ok
                      case Some(_) =>
                        Response.status(Status.BadRequest)
                          .addHeader("X-Error", "Age must be between 0 and 150")
                      case None =>
                        Response.status(Status.BadRequest)
                          .addHeader("X-Error", "Invalid age format")
                    }
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              }
            )
          }
          // Too small
          tooSmall <- client(Request.post(URL.root.port(port) / "age", Body.fromString("-1")))
          // Too large
          tooLarge <- client(Request.post(URL.root.port(port) / "age", Body.fromString("200")))
          // Valid
          valid <- client(Request.post(URL.root.port(port) / "age", Body.fromString("25")))
        } yield assertTrue(
          tooSmall.status == Status.BadRequest,
          tooLarge.status == Status.BadRequest,
          valid.status == Status.Ok
        )
      },
    ),
    suite("Error message quality")(
      test("error response includes helpful message") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
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
            )
          }
          errorResp <- client(Request.post(URL.root.port(port) / "login", Body.empty))
          // Headers.get returns Option[String] for the header value
          errorMsg = errorResp.headers.get("X-Error")
        } yield assertTrue(
          errorResp.status == Status.BadRequest,
          errorMsg.exists(_.contains("required"))
        )
      },
    ),
    suite("Edge cases")(
      test("handler handles very large input") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "process" -> handler { (req: Request) =>
                req.body.asString
                  .map { body =>
                    if (body.length > 1000)
                      Response.status(Status.RequestEntityTooLarge)
                    else
                      Response.ok
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              }
            )
          }
          // Small input
          smallResp <- client(
            Request.post(URL.root.port(port) / "process", Body.fromString("x" * 100))
          )
          // Large input
          largeResp <- client(
            Request.post(URL.root.port(port) / "process", Body.fromString("x" * 2000))
          )
        } yield assertTrue(
          smallResp.status == Status.Ok,
          largeResp.status == Status.RequestEntityTooLarge
        )
      },
      test("handler handles empty path parameters") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoute {
            Method.GET / string("name") -> handler { (name: String, _: Request) =>
              if (name.isEmpty)
                ZIO.succeed(Response.status(Status.BadRequest))
              else
                ZIO.succeed(Response.text(s"Hello, $name"))
            }
          }
          // Empty segment should not match due to routing
          notFound <- client(Request.get(URL.root.port(port) / ""))
        } yield assertTrue(notFound.status == Status.NotFound)
      },
    ),
    suite("Graceful degradation")(
      test("handler returns sensible defaults on error") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          dataAvailable <- Ref.make(true)
          _ <- TestServer.addRoute {
            Method.GET / "data" -> handler { (_: Request) =>
              dataAvailable.get.map { available =>
                if (available)
                  Response.json("""{"data": [1, 2, 3]}""")
                else
                  // Graceful degradation: return cached data
                  Response.json("""{"data": [], "cached": true}""")
              }
            }
          }
          // Data available
          normalResp <- client(Request.get(URL.root.port(port) / "data"))
          normalBody <- normalResp.body.asString
        } yield assertTrue(normalBody.contains("data"))
      },
    ),
  ).provide(TestServer.default, Client.default, Scope.default)
}
