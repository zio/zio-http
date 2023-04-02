/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint

import zio.test.{ZIOSpecDefault, assertTrue}
import zio.{ZIO, ZLayer}

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
      URL.decode("http://localhost:8080").getOrElse(???),
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
      ZLayer.succeed(Server.Config.default),
      Client.customized,
      ClientDriver.shared,
      executorLayer,
      NettyDriver.live,
      ZLayer.succeed(ZClient.Config.default),
      DnsResolver.default,
    )
}
