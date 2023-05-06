package example

import zio._
import zio.cli._

import zio.schema._

import zio.http.Header.Location
import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.cli._

trait TestCliEndpoints {
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
    Endpoint
      .get("users" / int("userId") ?? Doc.p("The unique identifier of the user"))
      .header(HeaderCodec.location ?? Doc.p("The user's location"))
      .out[User] ?? Doc.p("Get a user by ID")

  val getUserPosts =
    Endpoint
      .get(
        "users" / int("userId") ?? Doc.p("The unique identifier of the user") /
          "posts" / int("postId") ?? Doc.p("The unique identifier of the post") :? paramStr("The user's name"),
      )
      .out[List[Post]] ?? Doc.p("Get a user's posts by userId and postId")

  val createUser =
    Endpoint
      .post("users")
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
      )
      .cliApp
}

object TestCliServer extends zio.ZIOAppDefault with TestCliEndpoints {
  val getUserRoute =
    getUser.implement { case (id, _) =>
      ZIO.succeed(User(id, "Juanito", Some("juanito@test.com")))
    }

  val getUserPostsRoute =
    getUserPosts.implement { case (userId, postId, name) =>
      ZIO.succeed(List(Post(userId, postId, name)))
    }

  val createUserRoute =
    createUser.implement { user =>
      ZIO.succeed(user.name)
    }

  val routes = getUserRoute ++ getUserPostsRoute ++ createUserRoute

  val run = Server.serve(routes.toApp).provide(Server.default)
}

object TestCliClient extends zio.ZIOAppDefault with TestCliEndpoints {
  val run =
    clientExample
      .provide(
        EndpointExecutor.make(serviceName = "test"),
        Client.default,
      )

  lazy val clientExample: URIO[EndpointExecutor[Unit], Unit] =
    for {
      executor <- ZIO.service[EndpointExecutor[Unit]]
      _        <- executor(getUser(42, Location.parse("some-location").toOption.get)).debug("result1")
      _        <- executor(getUserPosts(42, 200, "adam")).debug("result2")
      _        <- executor(createUser(User(2, "john", Some("john@test.com")))).debug("result3")
    } yield ()

}
