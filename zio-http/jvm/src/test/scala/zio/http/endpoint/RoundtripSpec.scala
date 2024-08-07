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

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import zio.stream.ZStream

import zio.schema.annotation.validate
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Authorization
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.authorization
import zio.http.codec.HttpContentCodec.protobuf
import zio.http.codec._
import zio.http.endpoint.EndpointSpec.ImageMetadata
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

object RoundtripSpec extends ZIOHttpSpec {
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

  case class Age(@validate(Validation.greaterThan(18)) ignoredFieldName: Int)
  object Age {
    implicit val schema: Schema[Age] = DeriveSchema.gen[Age]
  }

  final case class PostWithAge(id: Int, title: String, body: String, userId: Int, age: Age)

  object PostWithAge {
    implicit val schema: Schema[PostWithAge] = DeriveSchema.gen[PostWithAge]
  }

  def makeExecutor(client: Client, port: Int): EndpointExecutor[Unit] = {
    val locator = EndpointLocator.fromURL(
      URL.decode(s"http://localhost:$port").toOption.get,
    )

    EndpointExecutor(client, locator, ZIO.unit)
  }

  def makeExecutor[MI](client: Client, port: Int, middlewareInput: MI): EndpointExecutor[MI] = {
    val locator = EndpointLocator.fromURL(
      URL.decode(s"http://localhost:$port").toOption.get,
    )

    EndpointExecutor(client, locator, ZIO.succeed(middlewareInput))
  }

  def testEndpoint[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, EndpointMiddleware.None.type],
    route: Routes[Any, Nothing],
    in: In,
    out: Out,
  ): ZIO[Client with Server with Scope, Err, TestResult] =
    testEndpointZIO(endpoint, route, in, outF = { (value: Out) => assert(out)(equalTo(value)) })

  def testEndpointZIO[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, EndpointMiddleware.None.type],
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
      out    <- client.quick(in.updateURL(_.host("localhost").port(port))).orDie
      result <- outF(out)
    } yield result
  }

  def testEndpointError[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, EndpointMiddleware.None.type],
    route: Routes[Any, Nothing],
    in: In,
    err: Err,
  ): ZIO[Client with Server with Scope, Out, TestResult] =
    testEndpointErrorZIO(endpoint, route, in, errorF = { (value: Err) => assert(err)(equalTo(value)) })

  def testEndpointErrorZIO[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, EndpointMiddleware.None.type],
    route: Routes[Any, Nothing],
    in: In,
    errorF: Err => ZIO[Any, Nothing, TestResult],
  ): ZIO[Client with Server with Scope, Out, TestResult] =
    for {
      port <- Server.install(route)
      executorLayer = ZLayer(ZIO.service[Client].map(makeExecutor(_, port)))
      out    <- ZIO
        .service[EndpointExecutor[Unit]]
        .flatMap { executor =>
          executor.apply(endpoint.apply(in))
        }
        .provideSome[Client with Scope](executorLayer)
        .flip
      result <- errorF(out)
    } yield result

  def spec: Spec[Any, Any] =
    suite("RoundtripSpec")(
      test("simple get") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts" / int("postId")).out[Post]

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
      test("simple get with protobuf encoding via explicit media type") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
            .out[Post](MediaType.parseCustomMediaType("application/protobuf").get)
            .header(HeaderCodec.accept)

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction { case (userId, postId, _) =>
              Post(postId, "title", "body", userId)
            }
          }

        testEndpoint(
          usersPostAPI,
          Routes(usersPostHandler),
          (10, 20, Header.Accept(MediaType.parseCustomMediaType("application/protobuf").get)),
          Post(20, "title", "body", 10),
        ) && assertZIO(TestConsole.output)(contains("ContentType: application/protobuf\n"))
      },
      test("simple get with only protobuf encoding") {
        implicit def postCodec[T: Schema]: HttpContentCodec[T] = protobuf.only[T]
        val usersPostAPI                                       =
          Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
            .out[Post]
            .header(HeaderCodec.accept)

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction { case (userId, postId, _) =>
              Post(postId, "title", "body", userId)
            }
          }

        testEndpoint(
          usersPostAPI,
          Routes(usersPostHandler),
          (10, 20, Header.Accept(MediaType.parseCustomMediaType("application/protobuf").get)),
          Post(20, "title", "body", 10),
        ) && assertZIO(TestConsole.output)(contains("ContentType: application/protobuf\n"))
      },
      test("simple get with optional query params") {
        val api =
          Endpoint(GET / "users" / int("userId"))
            .query(HttpCodec.queryInt("id"))
            .query(HttpCodec.query("name").optional)
            .query(HttpCodec.query("details").optional)
            .query(HttpCodec.queryTo[Age]("age").optional)
            .out[PostWithAge]

        val handler =
          api.implementHandler {
            Handler.fromFunction { case (id, userId, name, details, age) =>
              PostWithAge(id, name.getOrElse("-"), details.getOrElse("-"), userId, age.getOrElse(Age(20)))
            }
          }

        testEndpoint(
          api,
          Routes(handler),
          (10, 20, None, Some("x"), None),
          PostWithAge(10, "-", "x", 20, Age(20)),
        ) && testEndpoint(
          api,
          Routes(handler),
          (10, 20, None, None, None),
          PostWithAge(10, "-", "-", 20, Age(20)),
        ) &&
        testEndpoint(
          api,
          Routes(handler),
          (10, 20, Some("x"), Some("y"), Some(Age(23))),
          PostWithAge(10, "x", "y", 20, Age(23)),
        )
      },
      test("simple get with query params that fails validation") {
        val api =
          Endpoint(GET / "users" / int("userId"))
            .query(HttpCodec.queryInt("id"))
            .query(HttpCodec.query("name").optional)
            .query(HttpCodec.query("details").optional)
            .query(HttpCodec.queryTo[Age]("age").optional)
            .out[PostWithAge]

        val handler =
          api.implementHandler {
            Handler.fromFunction { case (id, userId, name, details, age) =>
              PostWithAge(id, name.getOrElse("-"), details.getOrElse("-"), userId, age.getOrElse(Age(0)))
            }
          }

        testEndpoint(
          api,
          Routes(handler),
          (10, 20, Some("x"), Some("y"), Some(Age(17))),
          PostWithAge(10, "x", "y", 20, Age(17)),
        ).catchAllCause(t =>
          ZIO.succeed(
            assertTrue(
              t.dieOption.contains(
                HttpCodecError.CustomError(
                  name = "InvalidEntity",
                  message = "A well-formed entity failed validation: 17 should be greater than 18",
                ),
              ),
            ),
          ),
        )
      },
      test("throwing error in handler") {
        val api = Endpoint(POST / string("id") / "xyz" / string("name") / "abc")
          .query(QueryCodec.query("details"))
          .query(QueryCodec.query("args").optional)
          .query(QueryCodec.query("env").optional)
          .outError[String](Status.BadRequest)
          .out[String] ?? Doc.p("doc")

        @nowarn("msg=dead code")
        val handler = api.implementHandler {
          Handler.fromFunction { case (accountId, name, instanceName, args, env) =>
            throw new RuntimeException("I can't code")
            s"$accountId, $name, $instanceName, $args, $env"
          }
        }

        for {
          port     <- Server.install(handler.toRoutes)
          client   <- ZIO.service[Client]
          response <- client(
            Request.post(
              url = URL.decode(s"http://localhost:$port/123/xyz/456/abc?details=789").toOption.get,
              body = Body.empty,
            ),
          )
        } yield assert(extractStatus(response))(equalTo(Status.InternalServerError))
      },
      test("simple post with json body") {
        val api = Endpoint(POST / "test" / int("userId"))
          .in[Post]
          .out[String]

        val route = api.implementHandler {
          Handler.fromFunction { case (userId, post) =>
            s"userId: $userId, post: $post"
          }
        }

        testEndpoint(
          api,
          Routes(route),
          (11, Post(1, "title", "body", 111)),
          "userId: 11, post: Post(1,title,body,111)",
        )
      },
      test("byte stream input") {
        val api   = Endpoint(PUT / "upload").inStream[Byte].out[Long]
        val route = api.implementHandler {
          Handler.fromFunctionZIO { bytes =>
            bytes.runCount
          }
        }

        Random.nextBytes(1024 * 1024).flatMap { bytes =>
          testEndpoint(
            api,
            Routes(route),
            ZStream.fromChunk(bytes).rechunk(1024),
            1024 * 1024L,
          )
        }
      },
      test("byte stream output") {
        val api   = Endpoint(GET / "download").query(QueryCodec.queryInt("count")).outStream[Byte]
        val route = api.implementHandler {
          Handler.fromFunctionZIO { count =>
            Random.nextBytes(count).map(chunk => ZStream.fromChunk(chunk).rechunk(1024))
          }
        }

        testEndpointZIO(
          api,
          Routes(route),
          1024 * 1024,
          (stream: ZStream[Any, Nothing, Byte]) => stream.runCount.map(c => assert(c)(equalTo(1024L * 1024L))),
        )
      },
      test("multi-part input") {
        val api = Endpoint(POST / "test")
          .in[String]("name")
          .in[Int]("value")
          .in[Post]("post")
          .out[String]

        val route = api.implementHandler {
          Handler.fromFunction { case (name, value, post) =>
            s"name: $name, value: $value, post: $post"
          }
        }

        testEndpoint(
          api,
          Routes(route),
          ("name", 10, Post(1, "title", "body", 111)),
          "name: name, value: 10, post: Post(1,title,body,111)",
        )
      },
      test("endpoint error returned") {
        val api = Endpoint(POST / "test")
          .outError[String](Status.Custom(999))

        val route = api.implementHandler(Handler.fail("42"))

        testEndpointError(
          api,
          Routes(route),
          (),
          "42",
        )
      },
      test("Failed endpoint deserialization") {
        val endpoint =
          Endpoint(GET / "users" / int("userId")).out[Int].outError[Int](Status.Custom(999))

        val endpointWithAnotherSignature =
          Endpoint(GET / "users" / int("userId")).out[Int].outError[String](Status.Custom(999))

        val endpointRoute =
          endpoint.implementHandler {
            Handler.fromFunctionZIO { id =>
              ZIO.fail(id)
            }
          }

        val routes = endpointRoute.toRoutes

        for {
          port <- Server.install(routes)
          executorLayer = ZLayer(ZIO.serviceWith[Client](makeExecutor(_, port)))

          cause <- ZIO
            .serviceWithZIO[EndpointExecutor[Unit]] { executor =>
              executor.apply(endpointWithAnotherSignature.apply(42))
            }
            .provideSome[Client with Scope](executorLayer)
            .cause
        } yield assertTrue(
          cause.prettyPrint.contains(
            """zio.http.codec.HttpCodecError$MalformedBody: Malformed request body failed to decode: (expected '"' got '4')""",
          ),
        )
      },
      test("multi-part input with stream field") {
        val api = Endpoint(POST / "test")
          .in[String]("name")
          .in[Int]("value")
          .inStream[Byte]("file")
          .out[String]

        val route = api.implementHandler {
          Handler.fromFunctionZIO { case (name, value, file) =>
            file.runCount.map { n =>
              s"name: $name, value: $value, count: $n"
            }
          }
        }

        Random.nextBytes(1024 * 1024).flatMap { bytes =>
          testEndpoint(
            api,
            Routes(route),
            ("xyz", 100, ZStream.fromChunk(bytes).rechunk(1024)),
            s"name: xyz, value: 100, count: ${1024 * 1024}",
          )
        }
      },
      test("multi-part input with stream and invalid json field") {
        val api = Endpoint(POST / "test")
          .in[String]("name")
          .in[ImageMetadata]("metadata")
          .inStream[Byte]("file")
          .out[String]

        val route = api.implementHandler {
          Handler.fromFunctionZIO { case (name, metadata, file) =>
            file.runCount.map { n =>
              s"name: $name, metadata: $metadata, count: $n"
            }
          }
        }

        Random
          .nextBytes(1024 * 1024)
          .flatMap { bytes =>
            testEndpointCustomRequestZIO(
              Routes(route),
              Request.post(
                "/test",
                Body.fromMultipartForm(
                  Form(
                    FormField.textField("name", """"xyz"""", MediaType.application.`json`),
                    FormField.textField(
                      "metadata",
                      """{"description": "sample description", "modifiedAt": "2023-10-02T10:30:00.00Z"}""",
                      MediaType.application.`json`,
                    ),
                    FormField.streamingBinaryField(
                      "file",
                      ZStream.fromChunk(bytes).rechunk(1000),
                      MediaType.application.`octet-stream`,
                    ),
                  ),
                  Boundary("bnd1234"),
                ),
              ),
              response =>
                response.body.asString.map(s =>
                  assertTrue(
                    s == """"name: xyz, metadata: ImageMetadata(sample description,2023-10-02T10:30:00Z), count: 1048576"""",
                  ),
                ),
            )
          }
          .map { r =>
            assert(r.isFailure)(isTrue) // We expect it to fail but complete
          }
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
