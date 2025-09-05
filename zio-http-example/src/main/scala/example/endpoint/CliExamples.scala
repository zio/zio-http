//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "dev.zio::zio-schema:1.7.2"

package example.endpoint

import zio._
import zio.cli._

import zio.schema._
import zio.schema.annotation.description

import zio.http.Header.Location
import zio.http._
import zio.http.codec._
import zio.http.endpoint.cli._
import zio.http.endpoint.{Endpoint, EndpointExecutor}

final case class User(
  @description("The unique identifier of the User")
  id: Int,
  @description("The user's name")
  name: String,
  @description("The user's email")
  email: Option[String],
)
object User {
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
}
final case class Post(
  @description("The unique identifier of the User")
  userId: Int,
  @description("The unique identifier of the Post")
  postId: Int,
  @description("The post's contents")
  contents: String,
)
object Post {
  implicit val schema: Schema[Post] = DeriveSchema.gen[Post]
}

trait TestCliEndpoints {

  val getUser =
    Endpoint(Method.GET / "users" / int("userId") ?? Doc.p("The unique identifier of the user"))
      .header(HeaderCodec.location ?? Doc.p("The user's location"))
      .out[User] ?? Doc.p("Get a user by ID")

  val getUserPosts =
    Endpoint(
      Method.GET /
        "users" / int("userId") ?? Doc.p("The unique identifier of the user") /
        "posts" / int("postId") ?? Doc.p("The unique identifier of the post"),
    )
      .query(
        HttpCodec.query[String]("user-name") ?? Doc.p(
          "The user's name",
        ),
      )
      .out[List[Post]] ?? Doc.p("Get a user's posts by userId and postId")

  val createUser =
    Endpoint(Method.POST / "users")
      .in[User]
      .out[String] ?? Doc.p("Create a new user")
}

object TestCliApp extends zio.cli.ZIOCliDefault with TestCliEndpoints {
  val cliApp =
    HttpCliApp
      .fromEndpoints(
        name = "users-mgmt",
        version = "0.0.1",
        summary = HelpDoc.Span.text("Users management CLI"),
        footer = HelpDoc.p("Copyright 2023"),
        host = "localhost",
        port = 8080,
        endpoints = Chunk(getUser, getUserPosts, createUser),
        cliStyle = true,
      )
      .cliApp
}

object TestCliServer extends zio.ZIOAppDefault with TestCliEndpoints {
  val getUserRoute =
    getUser.implementHandler {
      Handler.fromFunctionZIO { case (id, _) =>
        ZIO.succeed(User(id, "Juanito", Some("juanito@test.com"))).debug("Hello")
      }
    }

  val getUserPostsRoute =
    getUserPosts.implementHandler {
      Handler.fromFunction { case (userId, postId, name) =>
        List(Post(userId, postId, name))
      }
    }

  val createUserRoute =
    createUser.implementHandler {
      Handler.fromFunction { user =>
        user.name
      }
    }

  val routes = Routes(getUserRoute, getUserPostsRoute, createUserRoute) @@ Middleware.debug

  val run = Server.serve(routes).provide(Server.default)
}

object TestCliClient extends zio.ZIOAppDefault with TestCliEndpoints {
  val run =
    clientExample
      .provide(
        EndpointExecutor.make(serviceName = "test"),
        Client.default,
      )

  def clientExample: URIO[EndpointExecutor[Any, Unit, Scope], Unit] =
    for {
      executor <- ZIO.service[EndpointExecutor[Any, Unit, Scope]]
      _        <- ZIO.scoped(executor(getUser(42, Location.parse("some-location").toOption.get))).debug("result1")
      _        <- ZIO.scoped(executor(getUserPosts(42, 200, "adam")).debug("result2"))
      _        <- ZIO.scoped(executor(createUser(User(2, "john", Some("john@test.com"))))).debug("result3")
    } yield ()

}
