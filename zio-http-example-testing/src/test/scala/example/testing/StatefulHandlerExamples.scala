package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Examples of testing stateful handlers.
 *
 * Many HTTP applications maintain state across requests - authentication,
 * rate limiting, caching, shopping carts, etc. These examples show how to
 * test handlers that modify state.
 */
object StatefulHandlerExamples extends ZIOSpecDefault {

  def spec = suite("Stateful Handler Examples")(
    suite("Counter/accumulator state")(
      test("handler maintains counter across requests") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Create a counter that persists across requests
          counter <- Ref.make(0)
          _ <- TestServer.addRoute {
            Method.GET / "increment" -> handler { (_: Request) =>
              counter.updateAndGet(_ + 1).map(n => Response.text(s"Count: $n"))
            }
          }
          // Make multiple requests and verify state changes
          resp1 <- client(Request.get(URL.root.port(port) / "increment"))
          body1 <- resp1.body.asString
          resp2 <- client(Request.get(URL.root.port(port) / "increment"))
          body2 <- resp2.body.asString
          resp3 <- client(Request.get(URL.root.port(port) / "increment"))
          body3 <- resp3.body.asString
        } yield assertTrue(
          body1 == "Count: 1",
          body2 == "Count: 2",
          body3 == "Count: 3"
        )
      },
      test("counter state is isolated per test") {
        // This test demonstrates that each test gets its own state
        // (each test has its own counter created fresh)
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          counter <- Ref.make(10) // Start at 10, different from other test
          _ <- TestServer.addRoute {
            Method.GET / "value" -> handler { (_: Request) =>
              counter.get.map(n => Response.text(s"Value: $n"))
            }
          }
          resp <- client(Request.get(URL.root.port(port) / "value"))
          body <- resp.body.asString
        } yield assertTrue(body == "Value: 10")
      },
    ),
    suite("Authentication state")(
      test("login creates session, subsequent requests use session") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Simulate session store
          sessions <- Ref.make(Map.empty[String, String]) // sessionId -> username
          sessionCounter <- Ref.make(0)
          _ <- TestServer.addRoutes {
            Routes(
              Method.POST / "login" -> handler { (req: Request) =>
                req.body.asString
                  .flatMap { username =>
                    for {
                      sessionId <- sessionCounter.updateAndGet(_ + 1)
                      _ <- sessions.update(_ + (s"session-$sessionId" -> username))
                    } yield Response.status(Status.Ok)
                      .addHeader("Set-Cookie", s"session=session-$sessionId")
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              },
              Method.GET / "profile" -> handler { (req: Request) =>
                val sessionOpt = req.headers.get("Cookie").flatMap { cookie =>
                  // Parse "session=session-1" -> "session-1"
                  cookie.split("=").drop(1).headOption
                }
                sessionOpt match {
                  case Some(sessionId) =>
                    sessions.get.map { sess =>
                      sess.get(sessionId) match {
                        case Some(username) => Response.json(s"""{"username": "$username"}""")
                        case None => Response.status(Status.Unauthorized)
                      }
                    }
                  case None =>
                    ZIO.succeed(Response.status(Status.Unauthorized))
                }
              }
            )
          }
          // Login
          loginResp <- client(Request.post(URL.root.port(port) / "login", Body.fromString("alice")))
          cookie = loginResp.headers.get("Set-Cookie")
          // Access protected resource with session
          profileResp <- client(
            Request.get(URL.root.port(port) / "profile")
              .addHeader("Cookie", cookie.getOrElse(""))
          )
          profileBody <- profileResp.body.asString
        } yield assertTrue(profileBody.contains("alice"))
      },
    ),
    suite("Request tracking state")(
      test("handler tracks all requests made") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Track all requests
          requestLog <- Ref.make[List[String]](Nil)
          _ <- TestServer.addRoute {
            Method.GET / trailing -> handler { (path: Path, _: Request) =>
              requestLog.update(_ :+ path.encode).map { _ =>
                Response.ok
              }
            }
          }
          // Make various requests
          _ <- client(Request.get(URL.root.port(port) / "api" / "users"))
          _ <- client(Request.get(URL.root.port(port) / "api" / "posts"))
          _ <- client(Request.get(URL.root.port(port) / "admin" / "settings"))
          // Verify all were logged
          requests <- requestLog.get
        } yield assertTrue(
          requests.contains("/api/users"),
          requests.contains("/api/posts"),
          requests.contains("/admin/settings")
        )
      },
    ),
    suite("Data store state")(
      test("handler can store and retrieve data") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Simple in-memory data store
          users <- Ref.make(Map.empty[Int, String])
          nextId <- Ref.make(1)
          _ <- TestServer.addRoutes {
            Routes(
              // Create user
              Method.POST / "users" -> handler { (req: Request) =>
                req.body.asString
                  .flatMap { name =>
                    for {
                      id <- nextId.updateAndGet(_ + 1)
                      _ <- users.update(_ + (id -> name))
                    } yield Response.status(Status.Created)
                      .addHeader("X-User-ID", id.toString)
                  }
                  .catchAll { _ =>
                    ZIO.succeed(Response.status(Status.BadRequest))
                  }
              },
              // Get user
              Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
                users.get.map { store =>
                  store.get(id) match {
                    case Some(name) => Response.json(s"""{"id": $id, "name": "$name"}""")
                    case None => Response.notFound
                  }
                }
              }
            )
          }
          // Create multiple users
          create1 <- client(Request.post(URL.root.port(port) / "users", Body.fromString("Alice")))
          id1 = create1.headers.get("X-User-ID")
          create2 <- client(Request.post(URL.root.port(port) / "users", Body.fromString("Bob")))
          id2 = create2.headers.get("X-User-ID")
          // Retrieve them
          user1Id = id1.map(_.toIntOption.getOrElse(1)).getOrElse(1)
          user2Id = id2.map(_.toIntOption.getOrElse(2)).getOrElse(2)
          get1 <- client(Request.get(URL.root.port(port) / "users" / user1Id.toString))
          body1 <- get1.body.asString
          get2 <- client(Request.get(URL.root.port(port) / "users" / user2Id.toString))
          body2 <- get2.body.asString
        } yield assertTrue(
          body1.contains("Alice"),
          body2.contains("Bob")
        )
      },
    ),
    suite("State modification patterns")(
      test("handler conditionally updates state") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          // Track request count per endpoint
          stats <- Ref.make(Map.empty[String, Int])
          _ <- TestServer.addRoute {
            Method.GET / string("endpoint") -> handler { (endpoint: String, _: Request) =>
              stats.update { current =>
                current + (endpoint -> (current.getOrElse(endpoint, 0) + 1))
              }.map { _ =>
                Response.ok
              }
            }
          }
          // Make requests to different endpoints
          _ <- client(Request.get(URL.root.port(port) / "users"))
          _ <- client(Request.get(URL.root.port(port) / "users"))
          _ <- client(Request.get(URL.root.port(port) / "posts"))
          // Check final stats
          finalStats <- stats.get
        } yield assertTrue(
          finalStats.get("users") == Some(2),
          finalStats.get("posts") == Some(1)
        )
      },
    ),
  ).provide(TestServer.default, Client.default, Scope.default)
}
