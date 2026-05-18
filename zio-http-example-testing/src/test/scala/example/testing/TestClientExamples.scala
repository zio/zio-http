package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Examples of TestClient patterns.
 *
 * TestClient is a mock HTTP client that you can configure to return specific
 * responses for specific requests. Use it when your application makes HTTP calls
 * to external services and you want to mock those dependencies.
 */
object TestClientExamples extends ZIOSpecDefault {

  def spec = suite("TestClient Examples")(
    suite("Simple request/response mappings")(
      test("mock exact request-response pair") {
        for {
          client <- ZIO.service[Client]
          // Configure the mock: when this exact request is made, return this response
          _ <- TestClient.addRequestResponse(
            Request.get(URL.root / "users" / "1"),
            Response.json("""{"id": 1, "name": "Alice", "email": "alice@example.com"}""")
          )
          // Your code calls the client with the request
          response <- client(Request.get(URL.root / "users" / "1"))
          body <- response.body.asString
        } yield assertTrue(body.contains("Alice"))
      },
      test("mock multiple different endpoints") {
        for {
          client <- ZIO.service[Client]
          // Add multiple mappings
          _ <- TestClient.addRequestResponse(
            Request.get(URL.root / "users" / "1"),
            Response.json("""{"id": 1, "name": "Alice"}""")
          )
          _ <- TestClient.addRequestResponse(
            Request.get(URL.root / "users" / "2"),
            Response.json("""{"id": 2, "name": "Bob"}""")
          )
          // Request each endpoint
          response1 <- client(Request.get(URL.root / "users" / "1"))
          body1 <- response1.body.asString
          response2 <- client(Request.get(URL.root / "users" / "2"))
          body2 <- response2.body.asString
        } yield assertTrue(
          body1.contains("Alice"),
          body2.contains("Bob")
        )
      },
      test("mock different HTTP methods") {
        for {
          client <- ZIO.service[Client]
          // Mock a GET
          _ <- TestClient.addRequestResponse(
            Request.get(URL.root / "items"),
            Response.json("""[{"id": 1}]""")
          )
          // Mock a POST
          _ <- TestClient.addRequestResponse(
            Request.post(URL.root / "items", Body.fromString("""{"name": "New"}""")),
            Response.status(Status.Created)
          )
          getResp <- client(Request.get(URL.root / "items"))
          postResp <- client(Request.post(URL.root / "items", Body.fromString("""{"name": "New"}""")))
        } yield assertTrue(
          getResp.status == Status.Ok,
          postResp.status == Status.Created
        )
      },
    ),
    suite("Flexible route handlers")(
      test("handler can respond dynamically to path parameters") {
        for {
          client <- ZIO.service[Client]
          // Instead of exact mappings, use a handler that can respond to different requests
          _ <- TestClient.addRoute {
            Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
              ZIO.succeed(Response.json(s"""{"id": $id, "name": "User $id"}"""))
            }
          }
          // Now any GET /users/{id} request will be handled
          response1 <- client(Request.get(URL.root / "users" / "1"))
          body1 <- response1.body.asString
          response2 <- client(Request.get(URL.root / "users" / "99"))
          body2 <- response2.body.asString
        } yield assertTrue(
          body1.contains("User 1"),
          body2.contains("User 99")
        )
      },
      test("handler can perform computation on request") {
        for {
          client <- ZIO.service[Client]
          _ <- TestClient.addRoutes {
            Routes(
              Method.POST / "calculate" -> handler { (req: Request) =>
                // Handler can read the request and compute response
                req.body.asString
                  .map { body =>
                    val number = body.toIntOption.getOrElse(0)
                    Response.json(s"""{"result": ${number * 2}}""")
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              }
            )
          }
          response <- client(Request.post(URL.root / "calculate", Body.fromString("21")))
          body <- response.body.asString
        } yield assertTrue(body.contains("42"))
      },
    ),
    suite("Fallback handlers for tracking")(
      test("fallback handler captures unexpected requests") {
        for {
          client <- ZIO.service[Client]
          // Track any unexpected requests
          unexpectedCalls <- Ref.make[List[Request]](Nil)
          _ <- TestClient.setFallbackHandler { req =>
            unexpectedCalls.update(_ :+ req).as(Response.notFound)
          }
          // Configure an expected route
          _ <- TestClient.addRoute {
            Method.GET / "expected" -> handler(Response.ok)
          }
          // Make expected request
          _ <- client(Request.get(URL.root / "expected"))
          // Make unexpected request
          _ <- client(Request.get(URL.root / "unexpected"))
          // Verify what was captured
          calls <- unexpectedCalls.get
        } yield assertTrue(
          calls.length == 1,
          calls.head.url.path.encode == "/unexpected"
        )
      },
      test("fallback handler verifies no extra calls made") {
        for {
          client <- ZIO.service[Client]
          callCount <- Ref.make(0)
          _ <- TestClient.setFallbackHandler { _req =>
            callCount.update(_ + 1).as(Response.notFound)
          }
          _ <- TestClient.addRoute {
            Method.GET / "api" / "data" -> handler(Response.ok)
          }
          // Make the expected calls
          _ <- client(Request.get(URL.root / "api" / "data"))
          _ <- client(Request.get(URL.root / "api" / "data"))
          // Make one unexpected call
          _ <- client(Request.get(URL.root / "api" / "other"))
          count <- callCount.get
        } yield assertTrue(count == 1) // Only the unexpected call hit the fallback
      },
    ),
    suite("Route accumulation")(
      test("routes accumulate, new routes don't replace old ones") {
        for {
          client <- ZIO.service[Client]
          // Add first route
          _ <- TestClient.addRoute {
            Method.GET / "first" -> handler(Response.text("First"))
          }
          resp1 <- client(Request.get(URL.root / "first"))
          body1 <- resp1.body.asString
          // Add second route
          _ <- TestClient.addRoute {
            Method.GET / "second" -> handler(Response.text("Second"))
          }
          // Both routes should still work
          resp2 <- client(Request.get(URL.root / "first"))
          resp3 <- client(Request.get(URL.root / "second"))
          body2 <- resp2.body.asString
          body3 <- resp3.body.asString
        } yield assertTrue(
          body1 == "First",
          body2 == "First",
          body3 == "Second"
        )
      },
    ),
    suite("Error responses")(
      test("mock error responses from external service") {
        for {
          client <- ZIO.service[Client]
          // Mock an external service that returns errors
          _ <- TestClient.addRoute {
            Method.GET / "external" / "unavailable" -> handler(Response.status(Status.ServiceUnavailable))
          }
          response <- client(Request.get(URL.root / "external" / "unavailable"))
        } yield assertTrue(response.status == Status.ServiceUnavailable)
      },
      test("mock authentication failures") {
        for {
          client <- ZIO.service[Client]
          _ <- TestClient.addRoute {
            Method.GET / "protected" -> handler {
              Response.status(Status.Unauthorized)
                .addHeader("WWW-Authenticate", "Bearer realm=\"api\"")
            }
          }
          response <- client(Request.get(URL.root / "protected"))
        } yield assertTrue(response.status == Status.Unauthorized)
      },
    ),
  ).provide(TestClient.layer, Scope.default)
}
