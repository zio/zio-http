package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Examples of TestServer patterns.
 *
 * TestServer is used for integration testing - it starts a test server that
 * responds to HTTP requests according to defined routes. This is more realistic
 * than direct route testing and is useful for testing feature workflows.
 */
object TestServerExamples extends ZIOSpecDefault {

  def spec = suite("TestServer Examples")(
    suite("Basic server setup")(
      test("server responds to simple request") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Configure a simple route
          _ <- TestServer.addRoute {
            Method.GET / "hello" -> Handler.text("Hello, World!")
          }
          // Make HTTP request to the server
          response <- client(Request.get(URL.root.port(port) / "hello"))
          body <- response.body.asString
        } yield assertTrue(body == "Hello, World!")
      },
      test("server returns correct status codes") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoute {
            Method.POST / "items" -> Handler.status(Status.Created)
          }
          response <- client(Request.post(URL.root.port(port) / "items", Body.empty))
        } yield assertTrue(response.status == Status.Created)
      },
    ),
    suite("Multiple routes")(
      test("server handles multiple routes") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Add multiple routes at once
          _ <- TestServer.addRoutes {
            Routes(
              Method.GET / "health" -> Handler.text("OK"),
              Method.GET / "users" -> handler { (_: Request) =>
                ZIO.succeed(Response.json("""[{"id": 1}]"""))
              },
              Method.POST / "users" -> Handler.status(Status.Created),
            )
          }
          // Test each route
          healthResp <- client(Request.get(URL.root.port(port) / "health"))
          usersResp <- client(Request.get(URL.root.port(port) / "users"))
          createResp <- client(Request.post(URL.root.port(port) / "users", Body.empty))
          healthBody <- healthResp.body.asString
        } yield assertTrue(
          healthBody == "OK",
          usersResp.status == Status.Ok,
          createResp.status == Status.Created
        )
      },
      test("route precedence: specific routes before general") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // More specific route first, fallback last
          _ <- TestServer.addRoutes {
            Routes(
              Method.GET / "items" / int("id") -> handler { (id: Int, _: Request) =>
                ZIO.succeed(Response.json(s"""{"type": "specific", "id": $id}"""))
              },
              Method.GET / trailing -> handler { (_: Request) =>
                ZIO.succeed(Response.json("""{"type": "fallback"}"""))
              },
            )
          }
          // Request specific route
          specificResp <- client(Request.get(URL.root.port(port) / "items" / "42"))
          specificBody <- specificResp.body.asString
          // Request fallback route
          fallbackResp <- client(Request.get(URL.root.port(port) / "other"))
          fallbackBody <- fallbackResp.body.asString
        } yield assertTrue(
          specificBody.contains("specific"),
          fallbackBody.contains("fallback")
        )
      },
    ),
    suite("Request parameters and body")(
      test("handler can read request body") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "echo" -> handler { (req: Request) =>
                req.body.asString
                  .map { body =>
                    Response.text(s"Echo: $body")
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              }
            )
          }
          response <- client(Request.post(URL.root.port(port) / "echo", Body.fromString("Hello")))
          body <- response.body.asString
        } yield assertTrue(body == "Echo: Hello")
      },
      test("handler can extract path parameters") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoute {
            Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
              ZIO.succeed(Response.json(s"""{"id": $id, "name": "User $id"}"""))
            }
          }
          response <- client(Request.get(URL.root.port(port) / "users" / "5"))
          body <- response.body.asString
        } yield assertTrue(body.contains("User 5"))
      },
    ),
    suite("HTTP methods")(
      test("different methods on same path") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
              Method.GET / "resource" -> Handler.text("GET response"),
              Method.POST / "resource" -> Handler.text("POST response"),
              Method.PUT / "resource" -> Handler.text("PUT response"),
              Method.DELETE / "resource" -> Handler.text("DELETE response"),
            )
          }
          getResp <- client(Request.get(URL.root.port(port) / "resource"))
          postResp <- client(Request.post(URL.root.port(port) / "resource", Body.empty))
          putResp <- client(Request.put(URL.root.port(port) / "resource", Body.empty))
          deleteResp <- client(Request.delete(URL.root.port(port) / "resource"))
          getBody <- getResp.body.asString
          postBody <- postResp.body.asString
          putBody <- putResp.body.asString
          deleteBody <- deleteResp.body.asString
        } yield assertTrue(
          getBody == "GET response",
          postBody == "POST response",
          putBody == "PUT response",
          deleteBody == "DELETE response"
        )
      },
    ),
    suite("Error handling")(
      test("server returns not found for unmatched routes") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoute {
            Method.GET / "exists" -> Handler.ok
          }
          response <- client(Request.get(URL.root.port(port) / "missing"))
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("handler can return error responses") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "validate" -> handler { (req: Request) =>
                req.body.asString
                  .map { body =>
                    if (body.isEmpty)
                      Response.status(Status.BadRequest).addHeader("X-Error", "Body required")
                    else
                      Response.ok
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              }
            )
          }
          // Empty body -> error
          badResp <- client(Request.post(URL.root.port(port) / "validate", Body.empty))
          // Non-empty body -> success
          goodResp <- client(Request.post(URL.root.port(port) / "validate", Body.fromString("data")))
        } yield assertTrue(
          badResp.status == Status.BadRequest,
          goodResp.status == Status.Ok
        )
      },
    ),
    suite("Integration testing patterns")(
      test("test request-response flow") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Simulate a simple API that creates and retrieves items
          items <- Ref.make(Map.empty[Int, String])
          nextId <- Ref.make(1)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "items" -> handler { (req: Request) =>
                req.body.asString
                  .flatMap { name =>
                    for {
                      id <- nextId.updateAndGet(_ + 1)
                      _ <- items.update(_ + (id -> name))
                    } yield Response.status(Status.Created)
                      .addHeader("X-Item-ID", id.toString)
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              },
              Method.GET / "items" / int("id") -> handler { (id: Int, _: Request) =>
                items.get.map { itemMap =>
                  itemMap.get(id) match {
                    case Some(name) => Response.json(s"""{"id": $id, "name": "$name"}""")
                    case None => Response.notFound
                  }
                }
              }
            )
          }
          // Create an item
          createResp <- client(Request.post(URL.root.port(port) / "items", Body.fromString("Item 1")))
          itemId = createResp.headers.get("X-Item-ID")
          // Retrieve the created item
          id = itemId.map(_.toIntOption.getOrElse(1)).getOrElse(1)
          getResp <- client(Request.get(URL.root.port(port) / "items" / id.toString))
          getBody <- getResp.body.asString
        } yield assertTrue(
          createResp.status == Status.Created,
          getResp.status == Status.Ok,
          getBody.contains("Item 1")
        )
      },
    ),
  ).provide(TestServer.default, Client.default, Scope.default)
}
