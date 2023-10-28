package example

import zio._
import zio.cli._

import zio.schema._
import zio.schema.annotation.description

import zio.http.Header.Location
import zio.http._
import zio.http.codec._
import zio.http.endpoint.cli._
import zio.http.endpoint.{Endpoint, EndpointExecutor}

trait TestCliEndpoints {
  import zio.http.codec.PathCodec._

  import HttpCodec._
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
        paramStr("user-name") ?? Doc.p(
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
    getUser.implement {
      Handler.fromFunction { case (id, _) =>
        User(id, "Juanito", Some("juanito@test.com"))
      }
    }

  val getUserPostsRoute =
    getUserPosts.implement {
      Handler.fromFunction { case (userId, postId, name) =>
        List(Post(userId, postId, name))
      }
    }

  val createUserRoute =
    createUser.implement {
      Handler.fromFunction { user =>
        user.name
      }
    }

  val routes = Routes(getUserRoute, getUserPostsRoute, createUserRoute)

  val run = Server.serve(routes.toHttpApp).provide(Server.default)
}

object TestCliClient extends zio.ZIOAppDefault with TestCliEndpoints {
  val run =
    clientExample
      .provide(
        EndpointExecutor.make(serviceName = "test"),
        Client.default,
        Scope.default,
      )

  lazy val clientExample: URIO[EndpointExecutor[Unit] & Scope, Unit] =
    for {
      executor <- ZIO.service[EndpointExecutor[Unit]]
      _        <- executor(getUser(42, Location.parse("some-location").toOption.get)).debug("result1")
      _        <- executor(getUserPosts(42, 200, "adam")).debug("result2")
      _        <- executor(createUser(User(2, "john", Some("john@test.com")))).debug("result3")
    } yield ()

}
