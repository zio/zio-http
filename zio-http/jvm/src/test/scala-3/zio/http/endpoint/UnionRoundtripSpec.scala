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

import scala.annotation.nowarn
import scala.util.chaining.scalaUtilChainingOps

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import zio.stream.ZStream

import zio.schema.annotation.validate
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema}

import zio.http.Method._
import zio.http._
import zio.http.codec.HttpContentCodec.protobuf
import zio.http.codec._
import zio.http.netty.NettyConfig

object UnionRoundtripSpec extends ZIOHttpSpec {
  val testLayer: ZLayer[Any, Throwable, Server & Client & Scope] =
    ZLayer.make[Server & Client & Scope](
      Server.customized,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort.enableRequestStreaming),
      Client.customized.map(env => ZEnvironment(env.get)),
      ClientDriver.shared,
      // NettyDriver.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      ZLayer.succeed(ZClient.Config.default),
      DnsResolver.default,
      Scope.default,
      )

  def extractStatus(response: Response): Status = response.status

  trait PostsService {
    def getPost(userId: Int, postId: Int): ZIO[Any, Throwable, Post]
  }

  final case class Post(id: Int, title: String, body: String, userId: Int)

  object Post {
    implicit val schema: Schema[Post] = DeriveSchema.gen[Post]
  }

  case class Age(@validate(Validation.greaterThan(18)) age: Int)
  object Age {
    implicit val schema: Schema[Age] = DeriveSchema.gen[Age]
  }

  final case class PostWithAge(id: Int, title: String, body: String, userId: Int, age: Age)

  object PostWithAge {
    implicit val schema: Schema[PostWithAge] = DeriveSchema.gen[PostWithAge]
  }

  case class Outs(ints: List[Int])

  implicit val outsSchema: Schema[Outs] = DeriveSchema.gen[Outs]

  def makeExecutor(client: Client, port: Int) = {
    val locator = EndpointLocator.fromURL(
      URL.decode(s"http://localhost:$port").toOption.get,
      )

    EndpointExecutor(client, locator)
  }

  def testEndpoint[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    out: Out,
  ): ZIO[Client with Server with Scope, Err, TestResult] =
    testEndpointZIO(endpoint, route, in, outF = { (value: Out) => assert(out)(equalTo(value)) })

  def testEndpointZIO[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    outF: Out => ZIO[Any, Err, TestResult],
  ): zio.ZIO[Server with Client with Scope, Err, TestResult] =
    for {
      port   <- Server.install(route @@ Middleware.requestLogging())
      client <- ZIO.service[Client]
      executor = makeExecutor(client, port)
      out    <- executor(endpoint.apply(in))
      result <- outF(out)
    } yield result

  def testEndpointCustomRequestZIO[P, In, Err, Out](
    route: Routes[Any, Nothing],
    in: Request,
    outF: Response => ZIO[Any, Err, TestResult],
  ): zio.ZIO[Server with Client with Scope, Err, TestResult] = {
    for {
      port   <- Server.install(route @@ Middleware.requestLogging())
      client <- ZIO.service[Client]
      out    <- client.batched(in.updateURL(_.host("localhost").port(port))).orDie
      result <- outF(out)
    } yield result
  }

  def testEndpointError[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    err: Err,
  ): ZIO[Client with Server with Scope, Out, TestResult] =
    testEndpointErrorZIO(endpoint, route, in, errorF = { (value: Err) => assert(err)(equalTo(value)) })

  def testEndpointErrorZIO[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    errorF: Err => ZIO[Any, Nothing, TestResult],
  ): ZIO[Client with Server with Scope, Out, TestResult] =
    for {
      port <- Server.install(route)
      executorLayer = ZLayer(ZIO.service[Client].map(makeExecutor(_, port)))
      out    <- ZIO
        .service[EndpointExecutor[Any, Unit]]
        .flatMap { executor =>
          executor.apply(endpoint.apply(in))
        }
        .provideSome[Client with Scope](executorLayer)
        .flip
      result <- errorF(out)
    } yield result

  case class Params(
    int: Int,
    optInt: Option[Int] = None,
    string: String,
    strings: Chunk[String] = Chunk("defaultString"),
  )
  implicit val paramsSchema: Schema[Params]   = DeriveSchema.gen[Params]

  def spec: Spec[Any, Any] =
    suite("UnionRoundtripSpec")(
      test("simple get with right Unit union") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts-5" / int("postId")).out[Post].orOut[Unit](Status.BadRequest)

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction { case (userId, postId) =>
              Post(postId, "title", "body", userId)
            }
          }

        testEndpoint(
          usersPostAPI,
          Routes(usersPostHandler),
          (10, 20),
          Post(20, "title", "body", 10),
          )
      },
      test("simple get with left Unit union") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts-4" / int("postId")).out[Unit](Status.BadRequest).orOut[Post]

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction { case (userId, postId) =>
              Post(postId, "title", "body", userId)
            }
          }

        testEndpoint(
          usersPostAPI,
          Routes(usersPostHandler),
          (10, 20),
          Post(20, "title", "body", 10),
          )
      },
      test("simple get with right String union") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts-3" / int("postId")).out[Post].orOut[String](Status.BadRequest)

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction { case (userId, postId) =>
              Post(postId, "title", "body", userId)
            }
          }

        testEndpoint(
          usersPostAPI,
          Routes(usersPostHandler),
          (10, 20),
          Post(20, "title", "body", 10),
          )
      },
      test("simple get with left String union") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts-2" / int("postId")).out[String].orOut[Post]

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction { case (userId, postId) =>
              Post(postId, "title", "body", userId)
            }
          }

        testEndpoint(
          usersPostAPI,
          Routes(usersPostHandler),
          (10, 20),
          Post(20, "title", "body", 10),
          )
      },
      test("simple get with same type union") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts-1" / int("postId")).out[Post].orOut[Post](Status.BadRequest)

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction { case (userId, postId) =>
              Post(postId, "title", "body", userId)
            }
          }

        testEndpoint(
          usersPostAPI,
          Routes(usersPostHandler),
          (10, 20),
          Post(20, "title", "body", 10),
          )
      },
      test("endpoint error with right Unit union returned") {
        val api = Endpoint(POST / "test-1")
          .outError[String](Status.Custom(999)).orOutError[Unit](Status.BadRequest)

        val route = api.implementHandler(Handler.fail("42"))

        testEndpointError(
          api,
          Routes(route),
          (),
          "42",
          )
      },
      test("endpoint error with left Unit union returned") {
        val api = Endpoint(POST / "test-2")
          .outError[Unit](Status.BadRequest).orOutError[String](Status.Custom(999))

        val route = api.implementHandler(Handler.fail("42"))

        testEndpointError(
          api,
          Routes(route),
          (),
          "42",
          )
      },
      test("endpoint error with right Int union returned") {
        val api = Endpoint(POST / "test-3")
          .outError[String](Status.Custom(999)).orOutError[Int](Status.BadRequest)

        val route = api.implementHandler(Handler.fail("42"))

        testEndpointError(
          api,
          Routes(route),
          (),
          "42",
          )
      },
      test("endpoint error with left Int union returned") {
        val api = Endpoint(POST / "test-4")
          .outError[Int](Status.BadRequest).orOutError[String](Status.Custom(999))

        val route = api.implementHandler(Handler.fail("42"))

        testEndpointError(
          api,
          Routes(route),
          (),
          "42",
          )
      },
      test("endpoint error with same type union returned") {
        val api = Endpoint(POST / "test-5")
          .outError[String](Status.Custom(999)).orOutError[String](Status.BadRequest)

        val route = api.implementHandler(Handler.fail("42"))

        testEndpointError(
          api,
          Routes(route),
          (),
          "42",
          )
      },
      ).provide(
      Server.customized,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort.enableRequestStreaming),
      Client.customized.map(env => ZEnvironment(env.get @@ clientDebugAspect)),
      ClientDriver.shared,
      // NettyDriver.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      ZLayer.succeed(ZClient.Config.default),
      DnsResolver.default,
      Scope.default,
      ) @@ withLiveClock @@ sequential

  private def extraLogging: PartialFunction[Response, String] = { case r =>
    r.headers.get(Header.ContentType).map(_.renderedValue).mkString("ContentType: ", "", "")
  }
  private def clientDebugAspect                               =
    ZClientAspect.debug(extraLogging)
}
