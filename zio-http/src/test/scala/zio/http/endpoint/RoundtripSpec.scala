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
import zio.test._

import zio.stream.ZStream

import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Authorization
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{authorization, query}
import zio.http.codec.{Doc, HeaderCodec, HttpCodec, QueryCodec}
import zio.http.netty.server.NettyDriver

object RoundtripSpec extends ZIOHttpSpec {
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
    suite("RoundtripSpec")(
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

        Random.nextBytes(1024 * 1024).timed.flatMap { case (duration, bytes) =>
          ZIO.debug("generating bytes took " + duration.render) *>
            testEndpoint(
              api,
              Routes(route),
              ("xyz", 100, ZStream.fromChunk(bytes).rechunk(1024)),
              s"name: xyz, value: 100, count: ${1024 * 1024}",
            )
        }
      },
    ).provide(
      Server.live,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort.enableRequestStreaming),
      Client.customized.map(env => ZEnvironment(env.get @@ clientDebugAspect)),
      ClientDriver.shared,
      NettyDriver.live,
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
