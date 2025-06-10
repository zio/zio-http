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
import zio.test.TestAspect._
import zio.test._

import zio.stream.ZStream

import zio.http.codec.HttpContentCodec._
import zio.http.internal.{DynamicServer, RoutesRunnableSpec}
import zio.http.netty.NettyConfig

object ServerRuntimeSpec extends RoutesRunnableSpec {

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    ZLayer.make[TestEnvironment](
      testEnvironment,
      Runtime.setUnhandledErrorLogLevel(LogLevel.Warning),
    )

  private final class Foo

  override def spec =
    suite("ServerRuntimeSpec") {
      test("runtime flags are propagated") {
        val server = Routes(
          Method.GET / "test" -> handler(ZIO.runtimeFlags.map(f => Response.text(f.toString))),
        )
        ZIO.runtimeFlags.flatMap { outer =>
          ZIO
            .scoped(serve)
            .zipRight(server.deploy.body.run(path = Path.root / "test", method = Method.GET))
            .flatMap(_.asString(Charsets.Utf8))
            .map(b => assertTrue(b == outer.toString))
        }
      } +
        test("fiber refs are propagated") {
          val server = Routes(
            Method.GET / "test" -> handler(
              ZIO.getFiberRefs.map(f => Response.text(f.get(FiberRef.unhandledErrorLogLevel).get.toString)),
            ),
          )
          ZIO
            .scoped(serve)
            .zipRight(server.deploy.body.run(path = Path.root / "test", method = Method.GET))
            .flatMap(_.asString(Charsets.Utf8))
            .map(b => assertTrue(b == "Some(LogLevel(30000,WARN,4))"))
        } +
        test("environment contains only the specified services") {
          val server = Routes(
            Method.GET / "test" -> handler(
              ZIO.environment[Any].flatMap { env =>
                ZIO
                  .fromOption(env.getDynamic[Foo])
                  .orDieWith(_ => new Exception("Expected Foo to be in the environment"))
                  .as(Response.text(env.size.toString))
              },
            ),
          )
          ZIO
            .scoped(serve[Foo](server))
            .zipRight(server.deploy.body.run(path = Path.root / "test", method = Method.GET))
            .flatMap(_.asString(Charsets.Utf8))
            .map(b => assertTrue(b == "2")) // one extra for Scope
        } +
        test("with scope") {
          val ref    = Ref.unsafe.make(0)(zio.Unsafe)
          val routes = Routes(
            Method.GET / "test" -> handler(
              ZIO.addFinalizer(ref.set(1)).as(Response.text("ok")),
            ),
          )
          serve(routes)
            .zipRight(routes.deploy.body.run(path = Path.root / "test", method = Method.GET))
            .flatMap(_.asString(Charsets.Utf8))
            .map(b => assertTrue(b == "ok")) *> ref.get.map { v => assertTrue(v == 1) }
        } +
        test("with scope streaming") {
          val ref    = Ref.unsafe.make(0)(zio.Unsafe)
          val routes = Routes(
            Method.GET / "test" -> handler(
              Body
                .fromStreamEnv(
                  ZStream.fromZIO(
                    ZIO.addFinalizer(ref.set(1)) *> ref.get.flatMap(v =>
                      if (v == 0) Exit.succeed(Chunk.fromArray("ok".getBytes)) else Exit.fail(new Exception("error")),
                    ),
                  ),
                )
                .map(body =>
                  Response(
                    body = body,
                  ),
                )
                .orDie,
            ),
          )
          serve(routes)
            .zipRight(routes.deploy.body.run(path = Path.root / "test", method = Method.GET))
            .flatMap(_.asString(Charsets.Utf8))
            .map(b => assertTrue(b == "ok")) *> ref.get.map { v => assertTrue(v == 1) }
        } @@ TestAspect.flaky
    }
      .provide(
        Scope.default,
        DynamicServer.live,
        Server.customized,
        ZLayer.succeed(Server.Config.default),
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        Client.default,
        ZLayer.succeed(new Foo),
      ) @@ sequential @@ withLiveClock
}
