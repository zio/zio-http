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

import zio.schema._
import zio.schema.annotation.validate
import zio.schema.validation.Validation

import zio.http.Method._
import zio.http._
import zio.http.codec.HttpContentCodec.protobuf
import zio.http.codec._
import zio.http.endpoint.EndpointSpec.ImageMetadata
import zio.http.netty.NettyConfig

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

  case class TestGroups(groups: Set[String])

  object TestGroups {
    implicit val schema: Schema[TestGroups] = Schema[String].transform(
      (s: String) => TestGroups(s.split(",").toSet),
      (tg: TestGroups) => tg.groups.mkString(","),
    )
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

  case class OptOut(age: Option[Int], name: Option[String])

  implicit val optOutSchema: Schema[OptOut] = DeriveSchema.gen[OptOut]

  case class Name(firstName: String, lastName: String)

  implicit val nameSchema: Schema[Name] = DeriveSchema.gen[Name]

  def makeExecutor(client: ZClient[Any, Any, Body, Throwable, Response], port: Int) =
    EndpointExecutor(client, url"http://localhost:$port")

  def testEndpoint[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    out: Out,
  ): ZIO[ZClient[Any, Any, Body, Throwable, Response] with Server with Scope, Err, TestResult] =
    testEndpointZIO(endpoint, route, in, outF = { (value: Out) => assert(out)(equalTo(value)) })

  def testEndpointZIO[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    outF: Out => ZIO[Any, Err, TestResult],
  ): zio.ZIO[Server with ZClient[Any, Any, Body, Throwable, Response] with Scope, Err, TestResult] =
    for {
      port   <- Server.installRoutes(route @@ Middleware.requestLogging())
      client <- ZIO.service[ZClient[Any, Any, Body, Throwable, Response]]
      executor = makeExecutor(client, port)
      out    <- executor(endpoint.apply(in))
      result <- outF(out)
    } yield result

  def testEndpointCustomRequestZIO[P, In, Err, Out](
    route: Routes[Any, Nothing],
    in: Request,
    outF: Response => ZIO[Any, Err, TestResult],
  ): zio.ZIO[Server with ZClient[Any, Any, Body, Throwable, Response] with Scope, Err, TestResult] = {
    for {
      port   <- Server.installRoutes(route @@ Middleware.requestLogging())
      client <- ZIO.service[ZClient[Any, Any, Body, Throwable, Response]]
      out    <- client(in.updateURL(_.host("localhost").port(port))).orDie
      result <- outF(out)
    } yield result
  }

  def testEndpointError[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    err: Err,
  ): ZIO[ZClient[Any, Any, Body, Throwable, Response] with Server with Scope, Out, TestResult] =
    testEndpointErrorZIO(endpoint, route, in, errorF = { (value: Err) => assert(err)(equalTo(value)) })

  def testEndpointErrorZIO[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, AuthType.None],
    route: Routes[Any, Nothing],
    in: In,
    errorF: Err => ZIO[Any, Nothing, TestResult],
  ): ZIO[ZClient[Any, Any, Body, Throwable, Response] with Server with Scope, Out, TestResult] =
    for {
      port <- Server.installRoutes(route)
      executorLayer = ZLayer(ZIO.serviceWith[ZClient[Any, Any, Body, Throwable, Response]](makeExecutor(_, port)))
      out    <- ZIO
        .serviceWithZIO[EndpointExecutor[Any, Unit, Any]](_.apply(endpoint.apply(in)))
        .provideSome[ZClient[Any, Any, Body, Throwable, Response]](executorLayer)
        .flip
      result <- errorF(out)
    } yield result

  case class Params(
    int: Int,
    optInt: Option[Int] = None,
    string: String,
    strings: Chunk[String] = Chunk("defaultString"),
  )
  implicit val paramsSchema: Schema[Params]                                         = DeriveSchema.gen[Params]

  case class HeaderWrapper(value: String)

  implicit val headerWrapperSchema: Schema[HeaderWrapper] = Schema[String].transform(HeaderWrapper.apply, _.value)

  def spec: Spec[Any, Any] =
    suiteAll("RoundtripSpec") {
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
      }
      test("simple get without payload") {
        val healthCheckAPI     = Endpoint(GET / "health-check").out[Unit]
        val healthCheckHandler = healthCheckAPI.implementAs(())
        testEndpoint(healthCheckAPI, Routes(healthCheckHandler), (), ())
      }
      test("NoContent status returns empty body - issue #3861") {
        val noContentAPI     = Endpoint(DELETE / "posts" / int("postId")).out[Unit](Status.NoContent)
        val noContentHandler = noContentAPI.implementHandler {
          Handler.fromFunction { _ => () }
        }
        testEndpoint(noContentAPI, Routes(noContentHandler), 42, ())
      }
      test("NotModified status returns empty body - issue #3861") {
        val notModifiedAPI     = Endpoint(GET / "resource" / int("id")).out[Unit](Status.NotModified)
        val notModifiedHandler = notModifiedAPI.implementHandler {
          Handler.fromFunction { _ => () }
        }
        testEndpoint(notModifiedAPI, Routes(notModifiedHandler), 1, ())
      }
      test("simple get with query params from case class") {
        val endpoint = Endpoint(GET / "query")
          .query(HttpCodec.query[Params])
          .out[Params]
        val route    = endpoint.implementPurely(params => params)

        testEndpoint(
          endpoint,
          Routes(route),
          Params(1, Some(2), "string", Chunk("string1", "string2")),
          Params(1, Some(2), "string", Chunk("string1", "string2")),
        ) && testEndpoint(
          endpoint,
          Routes(route),
          Params(1, None, "string", Chunk("")),
          Params(1, None, "string", Chunk("")),
        )
      }
      test("Optional header") {
        val endpoint = Endpoint(GET / "query")
          .header(HeaderCodec.headerAs[String]("x-rd-modified-order-id").optional)
          .out[Option[String]]
        val route    = endpoint.implementPurely { header => header }

        testEndpoint(
          endpoint,
          Routes(route),
          Some("hallo"),
          Some("hallo"),
        )
      }
      test("Transformed schema header") {
        val endpoint = Endpoint(GET / "query")
          .header[HeaderWrapper]("x-rd-modified-order-id")
          .out[HeaderWrapper]
        val route    = endpoint.implementPurely { header => header }

        testEndpoint(
          endpoint,
          Routes(route),
          HeaderWrapper("hallo"),
          HeaderWrapper("hallo"),
        )
      }
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
      }
      test("simple get with only protobuf encoding") {
        implicit def postCodec[T: Schema]: HttpContentCodec[T] = protobuf.only[T]

        val usersPostAPI =
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
      }
      test("simple get with collection header") {
        val api = Endpoint(GET / "users")
          .header[Set[String]]("test-groups")
          .out[Set[String]]

        val handler =
          api.implementPurely(Predef.identity)

        testEndpoint(
          api,
          Routes(handler),
          Set("a", "b", "c"),
          Set("a", "b", "c"),
        )
      }
      test("simple get with transformed collection header") {
        implicit val testGroupsSchema: Schema[Set[String]] = Schema[String].transform(
          (s: String) => s.split(",").toSet,
          (tg: Set[String]) => tg.mkString(","),
        )
        val api                                            = Endpoint(GET / "users")
          .header[Set[String]]("test-groups")
          .out[Set[String]]

        val handler =
          api.implementPurely(Predef.identity)

        testEndpoint(
          api,
          Routes(handler),
          Set("a", "b", "c"),
          Set("a", "b", "c"),
        )
      }
      test("simple get with optional query params") {
        val api =
          Endpoint(GET / "users" / int("userId"))
            .query(HttpCodec.query[Int]("id"))
            .query(HttpCodec.query[String]("name").optional)
            .query(HttpCodec.query[String]("details").optional)
            .query(HttpCodec.query[Age].optional)
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
          (10, 20, Some("x"), Some("y"), Some(Age(23))),
          PostWithAge(10, "x", "y", 20, Age(23)),
        )
      }
      test("simple get with query params that fails validation") {
        val api =
          Endpoint(GET / "users" / int("userId"))
            .query(HttpCodec.query[Int]("id"))
            .query(HttpCodec.query[String]("name").optional)
            .query(HttpCodec.query[String]("details").optional)
            .query(HttpCodec.query[Age].optional)
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
      }
      test("simple get with case class in return and header codec accepting media type text plain") {
        val usersPostAPI =
          Endpoint(GET / "users" / "posts")
            .out[Post]
            .header(HeaderCodec.accept)

        val usersPostHandler =
          usersPostAPI.implementHandler {
            Handler.fromFunction(_ => Post(1, "title", "body", 3))
          }

        testEndpointCustomRequestZIO(
          usersPostHandler.toRoutes,
          Request(
            method = GET,
            url = URL(path = Path("/users/posts")),
            headers = Headers(Header.Accept(MediaType.text.`plain`)),
          ),
          response =>
            response.body.asString.map(s => assertTrue(s.contains("Unexpected error happened when encoding response"))),
        )
      }

      test("throwing error in handler") {
        val api = Endpoint(POST / string("id") / "xyz" / string("name") / "abc")
          .query(HttpCodec.query[String]("details"))
          .query(HttpCodec.query[String]("args").optional)
          .query(HttpCodec.query[String]("env").optional)
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
          port     <- Server.installRoutes(handler.toRoutes)
          client   <- ZIO.service[ZClient[Any, Any, Body, Throwable, Response]]
          response <- client(
            Request.post(
              url = URL.decode(s"http://localhost:$port/123/xyz/456/abc?details=789").toOption.get,
              body = Body.empty,
            ),
          )
        } yield assert(extractStatus(response))(equalTo(Status.InternalServerError))
      }
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
      }
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
      }
      test("byte stream output") {
        val api   = Endpoint(GET / "download").query(HttpCodec.query[Int]("count")).outStream[Byte]
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
      }
      test("string stream output") {
        val api   = Endpoint(GET / "download").query(HttpCodec.query[Int]("count")).outStream[String]
        val route = api.implementHandler {
          Handler.fromFunctionZIO { count =>
            ZIO.succeed(ZStream.fromIterable((0 until count).map(_.toString)))
          }
        }

        testEndpointZIO(
          api,
          Routes(route),
          1024 * 1024,
          (stream: ZStream[Any, Nothing, String]) =>
            stream.zipWithIndex
              .runFold((true, 0)) { case ((allOk, count), (str, idx)) =>
                (allOk && str == idx.toString, count + 1)
              }
              .map { case (allOk, c) =>
                assertTrue(allOk && c == 1024 * 1024)
              },
        )
      }
      test("string output") {
        val api   = Endpoint(GET / "download").query(HttpCodec.query[String]("param")).out[String]
        val route = api.implementHandler {
          Handler.fromFunctionZIO { param =>
            ZIO.succeed(param)
          }
        }

        testEndpointZIO(
          api,
          Routes(route),
          "test",
          (str: String) => assertTrue(str == "test"),
        )
      }
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
      }
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
      }
      test("endpoint error returned for outStream[Byte] - issue #3207") {
        // This test verifies that outError works correctly with outStream[Byte]
        // See: https://github.com/zio/zio-http/issues/3207
        val api = Endpoint(GET / "download")
          .query(HttpCodec.query[Boolean]("shouldFail"))
          .outStream[Byte]
          .outError[String](Status.Custom(999))

        val route = api.implementHandler {
          Handler.fromFunctionZIO { shouldFail =>
            if (shouldFail) ZIO.fail("error message")
            else ZIO.succeed(ZStream.fromChunk(Chunk.fromArray("success".getBytes)))
          }
        }

        testEndpointError(
          api,
          Routes(route),
          true,
          "error message",
        )
      }
      test("endpoint error status code for outStream[Byte] - issue #3207") {
        // This test verifies that the actual HTTP status code is correct with outStream[Byte]
        // See: https://github.com/zio/zio-http/issues/3207
        val api = Endpoint(GET / "download")
          .query(HttpCodec.query[Boolean]("shouldFail"))
          .outStream[Byte]
          .outError[String](Status.Custom(999))

        val route = api.implementHandler {
          Handler.fromFunctionZIO { shouldFail =>
            if (shouldFail) ZIO.fail("error message")
            else ZIO.succeed(ZStream.fromChunk(Chunk.fromArray("success".getBytes)))
          }
        }

        testEndpointCustomRequestZIO(
          Routes(route),
          Request.get(URL.root / "download").addQueryParam("shouldFail", "true"),
          response => assertTrue(response.status == Status.Custom(999)),
        )
      }
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
          port <- Server.installRoutes(routes)
          executorLayer = ZLayer(ZIO.serviceWith[ZClient[Any, Any, Body, Throwable, Response]](makeExecutor(_, port)))

          cause <- ZIO
            .serviceWithZIO[EndpointExecutor[Any, Unit, Any]](_.apply(endpointWithAnotherSignature.apply(42)))
            .provideSome[ZClient[Any, Any, Body, Throwable, Response]](executorLayer)
            .cause
        } yield assertTrue(
          cause.prettyPrint.contains(
            """zio.http.codec.HttpCodecError$MalformedBody: Malformed request body failed to decode: (expected string)""",
          ),
        )
      }
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
      }
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
      }
      test("Default CodecConfig writes empty collections") {
        val api = Endpoint(GET / "test").out[Outs]
        testEndpointCustomRequestZIO(
          api.implement(_ => ZIO.succeed(Outs(Nil))).toRoutes,
          Request.get("/test"),
          response => response.body.asString.map(s => assertTrue(s == """{"ints":[]}""")),
        )
      }
      test("Default CodecConfig writes empty options") {
        val api = Endpoint(GET / "test").out[OptOut]
        testEndpointCustomRequestZIO(
          api.implement(_ => ZIO.succeed(OptOut(None, None))).toRoutes,
          Request.get("/test"),
          response => response.body.asString.map(s => assertTrue(s == """{"age":null,"name":null}""")),
        )
      }
      test("Change CodecConfig to not encode empty collections") {
        val api = Endpoint(GET / "test").out[Outs]
        testEndpointCustomRequestZIO(
          api.implement(_ => ZIO.succeed(Outs(Nil))).toRoutes @@ CodecConfig.ignoringEmptyFields,
          Request.get("/test"),
          response => response.body.asString.map(s => assertTrue(s == """{}""")),
        )
      }
      test("Change CodecConfig to not encode empty Options") {
        val api = Endpoint(GET / "test").out[OptOut]
        testEndpointCustomRequestZIO(
          api.implement(_ => ZIO.succeed(OptOut(None, None))).toRoutes @@ CodecConfig.ignoringEmptyFields,
          Request.get("/test"),
          response => response.body.asString.map(s => assertTrue(s == """{}""")),
        )
      }
      test("Default CodecConfig accepts empty collections") {
        val api = Endpoint(GET / "test").in[OptOut].out[OptOut]
        testEndpointCustomRequestZIO(
          api.implementAs(OptOut(None, None)).toRoutes,
          Request.get("/test").withBody(Body.fromString("""{}""")),
          response => response.body.asString.map(s => assertTrue(s == """{"age":null,"name":null}""")),
        )
      }
      test("Default CodecConfig accepts empty collections") {
        val api = Endpoint(GET / "test").in[OptOut].out[OptOut]
        testEndpointCustomRequestZIO(
          api.implementAs(OptOut(None, None)).toRoutes,
          Request.get("/test").withBody(Body.fromString("""{}""")),
          response => response.body.asString.map(s => assertTrue(s == """{"age":null,"name":null}""")),
        )
      }
      test("Default CodecConfig does not change casing") {
        val api = Endpoint(GET / "test").in[Name].out[Name]
        testEndpointCustomRequestZIO(
          api.implementAs(Name("hans", "maier")).toRoutes,
          Request.get("/test").withBody(Body.fromString("""{"firstName": "hans", "lastName": "maier"}""")),
          response => response.body.asString.map(s => assertTrue(s == """{"firstName":"hans","lastName":"maier"}""")),
        )
      }
      test("SnakeCase via CodecConfig") {
        val api = Endpoint(GET / "test").in[Name].out[Name]
        testEndpointCustomRequestZIO(
          api.implementAs(Name("hans", "maier")).toRoutes @@ CodecConfig.withConfig(
            CodecConfig(fieldNameFormat = NameFormat.SnakeCase),
          ),
          Request.get("/test").withBody(Body.fromString("""{"first_name": "hans", "last_name": "maier"}""")),
          response => response.body.asString.map(s => assertTrue(s == """{"first_name":"hans","last_name":"maier"}""")),
        )
      }
      test("Reject exra fields via CodecConfig") {
        val api = Endpoint(GET / "test").in[Name].out[Name]
        testEndpointCustomRequestZIO(
          api.implementAs(Name("hans", "maier")).toRoutes @@ CodecConfig.withConfig(
            CodecConfig(rejectExtraFields = true),
          ),
          Request.get("/test").withBody(Body.fromString("""{"firstName": "hans", "lastName": "maier", "age": 42}""")),
          response =>
            response.body.asString.map(s =>
              assertTrue(s.contains("Malformed request body failed to decode: (extra field)")),
            ),
        )
      }

    }.provide(
      Server.customized,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort.enableRequestStreaming),
      Client.customized.map(env => ZEnvironment(env.get @@ clientDebugAspect)) >>>
        ZLayer(ZIO.serviceWith[Client](_.batched)),
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
