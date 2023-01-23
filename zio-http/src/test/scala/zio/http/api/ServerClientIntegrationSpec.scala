package zio.http.api

import zio.http.netty.client.{NettyClientDriver, NettyConnectionPool}
import zio.http.{Client, ClientConfig, Server, ServerConfig, URL}
import zio.schema.{DeriveSchema, Schema}
import zio.test.{ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

import scala.language.implicitConversions

object ServerClientIntegrationSpec extends ZIOSpecDefault {
  trait PostsService {
    def getPost(userId: Int, postId: Int): ZIO[Any, Throwable, Post]
  }

  final case class Post(id: Int, title: String, body: String, userId: Int)

  object Post {
    implicit val schema: Schema[Post] = DeriveSchema.gen[Post]
  }

  val usersPostAPI =
    EndpointSpec.get("users" / RouteCodec.int("userId") / "posts" / RouteCodec.int("postId")).out[Post]

  val usersPostHandler =
    usersPostAPI.implement { case (userId, postId) =>
      ZIO.succeed(Post(postId, "title", "body", userId))
    }

  // TODO: [Ergonomics] Need to make it easy to create an EndpointExecutor layer
  def makeExecutor(client: Client) = {
    val registry = EndpointRegistry(
      URL.fromString("http://localhost:8080").getOrElse(???),
      usersPostAPI.toServiceSpec ++ usersPostAPI.toServiceSpec,
    )

    EndpointExecutor(client, registry, ZIO.unit)
  }

  val executorLayer = ZLayer.fromFunction(makeExecutor _)

  def spec =
    suite("ServerClientIntegrationSpec")(
      test("server and client integration") {
        for {
          _        <- Server.install(usersPostHandler.toHttpApp)
          _        <- ZIO.debug("Installed server")
          executor <- ZIO.service[EndpointExecutor[Any, Any, usersPostAPI.type]]
          // QUESTION: Do we want to encode `E` in an API?
          // The result of `executor.apply` could be ApiError[E], a sealed trait of the user error E or
          // some network error Throwable. Is that worth it?
          result   <- executor(usersPostAPI(10, 20))
          _        <- ZIO.debug(s"Result: $result")
        } yield assertTrue(result == Post(20, "title", "body", 10))
      },
    ).provide(
      Server.live,
      ServerConfig.live,
      Client.live,
      executorLayer,
      // TODO: [Ergonomics] Server.default is a value and ClientConfig.default is a Layer
      ClientConfig.default,
      NettyClientDriver.fromConfig,
    )
}
