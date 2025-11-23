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

package zio.http

import zio._
import zio.test.Assertion._
import zio.test._

import zio.stream.ZStream

import zio.http.internal.{DynamicServer, RoutesRunnableSpec}
import zio.http.netty.NettyConfig

/**
 * Tests for Method.ANY routes with HEAD requests to verify that response bodies
 * are properly discarded.
 */
object MethodAnyHeadSpec extends RoutesRunnableSpec {

  private val configApp = Server.Config.default
    .requestDecompression(true)
    .disableRequestStreaming(1024 * 10)
    .port(8080)
    .responseCompression()

  private val app = serve

  def methodAnyHeadSpec = suite("MethodAnyHeadSpec")(
    test("Method.ANY route responds to HEAD request") {
      val routes = Routes(
        RoutePattern.any -> handler { (path: Path, _: Request) =>
          Response.text(s"Path: $path")
        },
      )
      for {
        response <- routes.deploy.run(method = Method.HEAD, path = Path.root / "any" / "path")
      } yield assertTrue(
        response.status == Status.Ok,
        response.headers.get(Header.ContentLength).isDefined,
      )
    },
    test("Method.ANY route discards body on HEAD request") {
      val routes = Routes(
        RoutePattern.any -> handler { (_: Path, _: Request) =>
          Response.text("This is the body content")
        },
      )
      for {
        response <- routes.deploy.run(method = Method.HEAD, path = Path.root / "test")
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.isEmpty,
        response.headers.get(Header.ContentLength).contains(Header.ContentLength(24L)),
      )
    },
    test("Method.ANY route returns body on GET request") {
      val routes = Routes(
        RoutePattern.any -> handler { (_: Path, _: Request) =>
          Response.text("This is the body content")
        },
      )
      for {
        response <- routes.deploy.run(method = Method.GET, path = Path.root / "test")
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == "This is the body content",
      )
    },
    test("Method.ANY route works for all standard methods") {
      val routes  = Routes(
        RoutePattern.any -> handler { (_: Path, req: Request) =>
          Response.text(s"Method: ${req.method.name}")
        },
      )
      val methods = List(Method.GET, Method.POST, Method.DELETE, Method.PATCH, Method.HEAD, Method.OPTIONS)

      for {
        responses <- ZIO.foreach(methods) { method =>
          routes.deploy.run(method = method, path = Path.root / "test").map(resp => (method, resp.status))
        }
      } yield assertTrue(responses.forall(_._2 == Status.Ok))
    },
    test("Method.ANY HEAD with streaming body discards content") {
      val routes = Routes(
        RoutePattern.any -> handler { (_: Path, _: Request) =>
          Response(
            status = Status.Ok,
            body = Body.fromStream(
              ZStream.fromIterable("Hello, World!".getBytes),
              "Hello, World!".getBytes.length.toLong,
            ),
          ).addHeader(Header.ContentLength(13L))
        },
      )
      for {
        response <- routes.deploy.run(method = Method.HEAD, path = Path.root / "stream")
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.isEmpty,
        response.headers.get(Header.ContentLength).contains(Header.ContentLength(13L)),
      )
    },
  )

  override def spec =
    suite("MethodAnyHeadSpec") {
      app.as(List(methodAnyHeadSpec))
    }.provideShared(
      Scope.default,
      DynamicServer.live,
      ZLayer.succeed(configApp),
      Server.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      Client.default,
    ) @@ TestAspect.sequential @@ TestAspect.withLiveClock

}
