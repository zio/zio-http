package zio.http.endpoint

import scala.language.implicitConversions

import zio.test.{ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.PathCodec.{int, literal}
import zio.http.netty.server.NettyDriver

object ServerClientIntegrationSpec extends ZIOSpecDefault {
  trait PostsService {
    def getPost(userId: Int, postId: Int): ZIO[Any, Throwable, Post]
  }

  final case class Post(id: Int, title: String, body: String, userId: Int)

  object Post {
    implicit val schema: Schema[Post] = DeriveSchema.gen[Post]
  }

  val usersPostAPI =
    Endpoint.get(literal("users") / int("userId") / literal("posts") / int("postId")).out[Post]

  val usersPostHandler =
    usersPostAPI.implement { case (userId, postId) =>
      ZIO.succeed(Post(postId, "title", "body", userId))
    }

  def makeExecutor(client: Client) = {
    val locator = EndpointLocator.fromURL(
      URL.fromString("http://localhost:8080").getOrElse(???),
    )

    EndpointExecutor(client, locator, ZIO.unit)
  }

  val executorLayer = ZLayer.fromFunction(makeExecutor _)

  def spec =
    suite("ServerClientIntegrationSpec")(
      test("server and client integration") {
        for {
          _        <- Server.install(usersPostHandler.toApp)
          _        <- ZIO.debug("Installed server")
          executor <- ZIO.service[EndpointExecutor[Unit]]
          result   <- executor(usersPostAPI(10, 20))
          _        <- ZIO.debug(s"Result: $result")
        } yield assertTrue(result == Post(20, "title", "body", 10))
      },
    ).provide(
      Server.live,
      ServerConfig.live,
      Client.live,
      ClientDriver.shared,
      executorLayer,
      NettyDriver.default,
      ClientConfig.default,
    )
}
