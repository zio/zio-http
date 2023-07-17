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

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.{Spec, TestResult, ZIOSpecDefault, assert}

import zio.stream.ZStream

import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Authorization
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{authorization, query}
import zio.http.codec.{Doc, HttpCodec, QueryCodec}
import zio.http.netty.server.NettyDriver

object EndpointRoundtripSpec extends ZIOSpecDefault {
  val testLayer: ZLayer[Any, Throwable, Server & Client & Scope] =
    ZLayer.make[Server & Client & Scope](
      Server.live,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort.enableRequestStreaming),
      Client.customized.map(env => ZEnvironment(env.get @@ ZClientAspect.debug)),
      ClientDriver.shared,
      NettyDriver.live,
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
      port   <- Server.install(route.toHttpApp @@ Middleware.requestLogging())
      client <- ZIO.service[Client]
      executor = makeExecutor(client, port)
      out    <- executor(endpoint.apply(in))
      result <- outF(out)
    } yield result

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
      port <- Server.install(route.toHttpApp)
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
    suite("EndpointRoundtripSpec")(
      test("simple get") {
        val usersPostAPI =
          Endpoint(GET / "users" / int("userId") / "posts" / int("postId")).out[Post]

        val usersPostHandler =
          usersPostAPI.implement {
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
      test("simple get with optional query params") {
        val api =
          Endpoint(GET / "users" / int("userId"))
            .query(HttpCodec.queryInt("id"))
            .query(HttpCodec.query("name").optional)
            .query(HttpCodec.query("details").optional)
            .out[Post]

        val handler =
          api.implement {
            Handler.fromFunction { case (id, userId, name, details) =>
              Post(id, name.getOrElse("-"), details.getOrElse("-"), userId)
            }
          }

        testEndpoint(
          api,
          Routes(handler),
          (10, 20, None, Some("x")),
          Post(10, "-", "x", 20),
        ) && testEndpoint(
          api,
          Routes(handler),
          (10, 20, None, None),
          Post(10, "-", "-", 20),
        ) &&
        testEndpoint(
          api,
          Routes(handler),
          (10, 20, Some("x"), Some("y")),
          Post(10, "x", "y", 20),
        )
      },
      test("throwing error in handler") {
        val api = Endpoint(POST / string("id") / "xyz" / string("name") / "abc")
          .query(query("details"))
          .query(query("args").optional)
          .query(query("env").optional)
          .outError[String](Status.BadRequest)
          .out[String] ?? Doc.p("doc")

        val handler = api.implement {
          Handler.fromFunction { case (accountId, name, instanceName, args, env) =>
            println(s"$accountId, $name, $instanceName, $args, $env")
            throw new RuntimeException("I can't code")
            s"$accountId, $name, $instanceName, $args, $env"
          }
        }

        for {
          port     <- Server.install(handler.toHttpApp)
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

        val route = api.implement {
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
        val route = api.implement {
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
        val route = api.implement {
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

        val route = api.implement {
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
      } @@ timeout(10.seconds) @@ ifEnvNotSet("CI"), // NOTE: random hangs on CI
      test("endpoint error returned") {
        val api = Endpoint(POST / "test")
          .outError[String](Status.Custom(999))

        val route = api.implement(Handler.fail("42"))

        testEndpointError(
          api,
          Routes(route),
          (),
          "42",
        )
      },
      test("middleware error returned") {

        val alwaysFailingMiddleware = EndpointMiddleware(
          authorization,
          HttpCodec.empty,
          HttpCodec.error[String](Status.Custom(900)),
        )

        val endpoint =
          Endpoint(GET / "users" / int("userId")).out[Int] @@ alwaysFailingMiddleware

        val endpointRoute =
          endpoint.implement(Handler.identity)

        val routes = endpointRoute

        val app = routes.toHttpApp @@ alwaysFailingMiddleware
          .implement(_ => ZIO.fail("FAIL"))(_ => ZIO.unit)

        for {
          port <- Server.install(app)
          executorLayer = ZLayer(ZIO.serviceWith[Client](makeExecutor(_, port, Authorization.Basic("user", "pass"))))

          out <- ZIO
            .serviceWithZIO[EndpointExecutor[alwaysFailingMiddleware.In]] { executor =>
              executor.apply(endpoint.apply(42))
            }
            .provideSome[Client & Scope](executorLayer)
            .flip
        } yield assert(out)(equalTo("FAIL"))
      },
      test("failed middleware deserialization") {
        val alwaysFailingMiddleware = EndpointMiddleware(
          authorization,
          HttpCodec.empty,
          HttpCodec.error[String](Status.Custom(900)),
        )

        val endpoint =
          Endpoint(GET / "users" / int("userId")).out[Int] @@ alwaysFailingMiddleware

        val alwaysFailingMiddlewareWithAnotherSignature = EndpointMiddleware(
          authorization,
          HttpCodec.empty,
          HttpCodec.error[Long](Status.Custom(900)),
        )

        val endpointWithAnotherSignature =
          Endpoint(GET / "users" / int("userId")).out[Int] @@ alwaysFailingMiddlewareWithAnotherSignature

        val endpointRoute =
          endpoint.implement(Handler.identity)

        val routes = endpointRoute

        val app = routes.toHttpApp @@ alwaysFailingMiddleware
          .implement(_ => ZIO.fail("FAIL"))(_ => ZIO.unit)

        for {
          port <- Server.install(app)
          executorLayer = ZLayer(ZIO.serviceWith[Client](makeExecutor(_, port, Authorization.Basic("user", "pass"))))

          cause <- ZIO
            .serviceWithZIO[EndpointExecutor[alwaysFailingMiddleware.In]] { executor =>
              executor.apply(endpointWithAnotherSignature.apply(42))
            }
            .provideSome[Client with Scope](executorLayer)
            .cause
        } yield assert(cause.prettyPrint)(
          containsString(
            "java.lang.IllegalStateException: Cannot deserialize using endpoint error codec",
          ),
        ) && assert(cause.prettyPrint)(
          containsString(
            "java.lang.IllegalStateException: Cannot deserialize using middleware error codec",
          ),
        ) && assert(cause.prettyPrint)(
          containsString(
            "Suppressed: java.lang.IllegalStateException: Trying to decode with Undefined codec.",
          ),
        ) && assert(cause.prettyPrint)(
          containsString(
            "Suppressed: zio.http.codec.HttpCodecError$MalformedBody: Malformed request body failed to decode: (expected a number, got F)",
          ),
        )
      },
      test("Failed endpoint deserialization") {
        val endpoint =
          Endpoint(GET / "users" / int("userId")).out[Int].outError[Int](Status.Custom(999))

        val endpointWithAnotherSignature =
          Endpoint(GET / "users" / int("userId")).out[Int].outError[String](Status.Custom(999))

        val endpointRoute =
          endpoint.implement {
            Handler.fromFunctionZIO { id =>
              ZIO.fail(id)
            }
          }

        val routes = endpointRoute

        val app = routes.toHttpApp

        for {
          port <- Server.install(app)
          executorLayer = ZLayer(ZIO.serviceWith[Client](makeExecutor(_, port)))

          cause <- ZIO
            .serviceWithZIO[EndpointExecutor[Unit]] { executor =>
              executor.apply(endpointWithAnotherSignature.apply(42))
            }
            .provideSome[Client with Scope](executorLayer)
            .cause
        } yield assert(cause.prettyPrint)(
          containsString(
            "java.lang.IllegalStateException: Cannot deserialize using endpoint error codec",
          ),
        ) && assert(cause.prettyPrint)(
          containsString(
            "java.lang.IllegalStateException: Cannot deserialize using middleware error codec",
          ),
        ) && assert(cause.prettyPrint)(
          containsString(
            "Suppressed: java.lang.IllegalStateException: Trying to decode with Undefined codec.",
          ),
        ) && assert(cause.prettyPrint)(
          containsString(
            """Suppressed: zio.http.codec.HttpCodecError$MalformedBody: Malformed request body failed to decode: (expected '"' got '4')""",
          ),
        )
      },
      test("multi-part input with stream field") {
        val api = Endpoint(POST / "test")
          .in[String]("name")
          .in[Int]("value")
          .inStream[Byte]("file")
          .out[String]

        val route = api.implement {
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
      } @@ timeout(10.seconds) @@ ifEnvNotSet("CI"), // NOTE: random hangs on CI
    ).provideLayer(testLayer) @@ withLiveClock @@ sequential @@ timeout(300.seconds)
}
