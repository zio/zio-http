package zio.http.api

import zio.http.{Client, ClientConfig, Server, ServerConfig, URL}
import zio.schema.{DeriveSchema, Schema}
import zio.test.{ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

import scala.language.implicitConversions

object ServerClientIntegrationSpec extends ZIOSpecDefault {
  implicit def stringToIn(s: String): RouteCodec[Unit] = RouteCodec.literal(s)

  trait PostsService {
    def getPost(userId: Int, postId: Int): ZIO[Any, Throwable, Post]
  }

  final case class Post(id: Int, title: String, body: String, userId: Int)

  object Post {
    implicit val schema: Schema[Post] = DeriveSchema.gen[Post]
  }

  val usersPostAPI     = API.get("users" / RouteCodec.int / "posts" / RouteCodec.int).out[Post]
  val usersPostHandler = usersPostAPI.implement { case (userId, postId) =>
    ZIO.succeed(Post(postId, "title", "body", userId))
  }

  // TODO: [Ergonomics] Need to make it easy to create an APIExecutor layer
  def makeExecutor(client: Client) = {
    val registry = APIRegistry(URL.fromString("http://localhost:8080").getOrElse(???), usersPostAPI ++ usersPostAPI)

    APIExecutor(client, registry, ZIO.unit)
  }

  val executorLayer = ZLayer.fromFunction(makeExecutor _)

  def spec =
    suite("ServerClientIntegrationSpec")(
      test("server and client integration") {
        for {
          _        <- Server.install(usersPostHandler.toHttpApp)
          _        <- ZIO.debug("Installed server")
          executor <- ZIO.service[APIExecutor[Any, Any, usersPostAPI.Id]]
          // QUESTION: Do we want to encode `E` in an API?
          // The result of `executor.apply` could be ApiError[E], a sealed trait of the user error E or
          // some network error Throwable. Is that worth it?
          result   <- executor(usersPostAPI(10, 20))
          _        <- ZIO.debug(s"Result: $result")
        } yield assertTrue(result == Post(20, "title", "body", 10))
      },
    ).provideSome[Scope](
      Server.live,
      ServerConfig.live,
      Client.live,
      executorLayer,
      // TODO: [Ergonomics] Server.default is a value and ClientConfig.default is a Layer
      ClientConfig.default,
    )
}
