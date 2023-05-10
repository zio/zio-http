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

import zio.test.TestAspect.{flaky, sequential, timeout, withLiveClock}
import zio.test.{TestResult, ZIOSpecDefault, assertTrue}
import zio.{Random, ZIO, ZLayer, durationInt}

import zio.stream.ZStream

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.PathCodec.{int, literal}
import zio.http.codec.QueryCodec
import zio.http.netty.server.NettyDriver

object ServerClientIntegrationSpec extends ZIOSpecDefault {
  trait PostsService {
    def getPost(userId: Int, postId: Int): ZIO[Any, Throwable, Post]
  }

  final case class Post(id: Int, title: String, body: String, userId: Int)

  object Post {
    implicit val schema: Schema[Post] = DeriveSchema.gen[Post]
  }

  def makeExecutor(client: Client, port: Int): EndpointExecutor[Unit] = {
    val locator = EndpointLocator.fromURL(
      URL.decode(s"http://localhost:$port").toOption.get,
    )

    EndpointExecutor(client, locator, ZIO.unit)
  }

  def testEndpoint[R, In, Err, Out](
    endpoint: Endpoint[In, Err, Out, EndpointMiddleware.None.type],
    route: Routes[R, Err, EndpointMiddleware.None.type],
    in: In,
    out: Out,
  ): ZIO[Client with R with Server, Err, TestResult] =
    testEndpointZIO(endpoint, route, in, outF = { (value: Out) => assertTrue(out == value) })

  def testEndpointZIO[R, In, Err, Out](
    endpoint: Endpoint[In, Err, Out, EndpointMiddleware.None.type],
    route: Routes[R, Err, EndpointMiddleware.None.type],
    in: In,
    outF: Out => ZIO[Any, Nothing, TestResult],
  ): ZIO[Client with R with Server, Err, TestResult] =
    for {
      port <- Server.install(route.toApp)
      executorLayer = ZLayer(ZIO.service[Client].map(makeExecutor(_, port)))
      out    <- ZIO
        .service[EndpointExecutor[Unit]]
        .flatMap { executor =>
          executor.apply(endpoint.apply(in))
        }
        .provideSome[Client](executorLayer)
      result <- outF(out)
    } yield result

  def spec =
    suite("ServerClientIntegrationSpec")(
      test("simple get") {
        val usersPostAPI =
          Endpoint.get(literal("users") / int("userId") / literal("posts") / int("postId")).out[Post]

        val usersPostHandler =
          usersPostAPI.implement { case (userId, postId) =>
            ZIO.succeed(Post(postId, "title", "body", userId))
          }

        testEndpoint(
          usersPostAPI,
          usersPostHandler,
          (10, 20),
          Post(20, "title", "body", 10),
        )
      },
      test("simple post with json body") {
        val api = Endpoint
          .post(literal("test") / int("userId"))
          .in[Post]
          .out[String]

        val route = api.implement { case (userId, post) =>
          ZIO.succeed(s"userId: $userId, post: $post")
        }

        testEndpoint(
          api,
          route,
          (11, Post(1, "title", "body", 111)),
          "userId: 11, post: Post(1,title,body,111)",
        )
      },
      test("byte stream input") {
        val api   = Endpoint.put(literal("upload")).inStream[Byte].out[Long]
        val route = api.implement { bytes =>
          bytes.runCount
        }

        Random.nextBytes(1024 * 1024).flatMap { bytes =>
          testEndpoint(
            api,
            route,
            ZStream.fromChunk(bytes).rechunk(1024),
            1024 * 1024L,
          )
        }
      },
      test("byte stream output") {
        val api   = Endpoint.get(literal("download")).query(QueryCodec.queryInt("count")).outStream[Byte]
        val route = api.implement { count =>
          Random.nextBytes(count).map(chunk => ZStream.fromChunk(chunk).rechunk(1024))
        }

        testEndpointZIO(
          api,
          route,
          1024 * 1024,
          (stream: ZStream[Any, Nothing, Byte]) => stream.runCount.map(c => assertTrue(c == 1024 * 1024)),
        )
      },
      test("multi-part input") {
        val api = Endpoint
          .post(literal("test"))
          .in[String]("name")
          .in[Int]("value")
          .in[Post]("post")
          .out[String]

        val route = api.implement { case (name, value, post) =>
          ZIO.succeed(s"name: $name, value: $value, post: $post")
        }

        testEndpoint(
          api,
          route,
          ("name", 10, Post(1, "title", "body", 111)),
          "name: name, value: 10, post: Post(1,title,body,111)",
        )
      } @@ timeout(10.seconds) @@ ignore, // TODO: investigate and fix,
      test("multi-part input with stream field") {
        val api = Endpoint
          .post(literal("test"))
          .in[String]("name")
          .in[Int]("value")
          .inStream[Byte]("file")
          .out[String]

        val route = api.implement { case (name, value, file) =>
          file.runCount.map { n =>
            s"name: $name, value: $value, count: $n"
          }
        }

        Random.nextBytes(1024 * 1024).flatMap { bytes =>
          testEndpoint(
            api,
            route,
            ("xyz", 100, ZStream.fromChunk(bytes).rechunk(1024)),
            s"name: xyz, value: 100, count: ${1024 * 1024}",
          )
        }
      } @@ timeout(10.seconds) @@ flaky, // TODO: investigate and fix
    ).provide(
      Server.live,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort.enableRequestStreaming),
      Client.customized,
      ClientDriver.shared,
      NettyDriver.live,
      ZLayer.succeed(ZClient.Config.default),
      DnsResolver.default,
    ) @@ withLiveClock @@ sequential @@ timeout(300.seconds)
}
