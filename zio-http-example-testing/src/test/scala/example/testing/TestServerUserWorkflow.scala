package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerUserWorkflow extends ZIOSpecDefault {
  def spec = test("complete user workflow") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)
      users <- Ref.make(Map.empty[Int, String])
      nextId <- Ref.make(0)

      _ <- TestServer.addRoutes {
        Routes(
          Method.POST / "users" -> handler { (req: Request) =>
            req.body.asString
              .flatMap { name =>
                for {
                  id <- nextId.updateAndGet(_ + 1)
                  _ <- users.update(_ + (id -> name))
                } yield Response.status(Status.Created).addHeader("X-ID", id.toString)
              }
              .catchAll { _ =>
                ZIO.succeed(Response.status(Status.BadRequest))
              }
          },
          Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
            users.get.map { store =>
              store.get(id) match {
                case Some(name) => Response.json(s"""{"id": $id, "name": "$name"}""")
                case None => Response.notFound
              }
            }
          },
        )
      }

      // Create a user
      createResp <- client(Request.post(URL.root.port(port) / "users", Body.fromString("Alice")))
      userId = createResp.headers.get("X-ID").flatMap(_.toIntOption).getOrElse(1)

      // Retrieve the user
      getResp <- client(Request.get(URL.root.port(port) / "users" / userId.toString))
      getBody <- getResp.body.asString
    } yield assertTrue(
      createResp.status == Status.Created,
      getResp.status == Status.Ok,
      getBody.contains("Alice")
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
