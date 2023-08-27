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

package zio.http.netty.client

import zio._
import zio.test.Assertion.{equalTo, hasSize}
import zio.test.TestAspect._
import zio.test._

import zio.stream.ZStream

import zio.http._
import zio.http.codec.PathCodec.trailing
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.netty.NettyConfig

object NettyConnectionPoolSpec extends HttpRunnableSpec {

  private val app = Routes(
    Method.POST / "streaming" -> handler((req: Request) => Response(body = Body.fromStream(req.body.asStream))),
    Method.GET / "slow"       -> handler(ZIO.sleep(1.hour).as(Response.text("done"))),
    Method.ANY / trailing     -> handler((_: Path, req: Request) => req.body.asString.map(Response.text(_))),
  ).sandbox.toHttpApp

  private val connectionCloseHeader = Headers(Header.Connection.Close)
  private val keepAliveHeader       = Headers(Header.Connection.KeepAlive)
  private val appKeepAliveEnabled   = serve

  private val N = 64

  def connectionPoolTests(
    version: Version,
    casesByHeaders: Map[String, Headers],
  ): Spec[Scope with Client with DynamicServer, Throwable] =
    suite(version.toString)(
      casesByHeaders.map { case (name, extraHeaders) =>
        suite(name)(
          test("not streaming") {
            val res =
              ZIO.foreachPar((1 to N).toList) { idx =>
                app
                  .deploy(
                    Request(
                      method = Method.POST,
                      body = Body.fromString(idx.toString),
                      headers = extraHeaders,
                    ),
                  )
                  .flatMap(_.body.asString)
              }
            assertZIO(res)(
              equalTo(
                (1 to N).map(_.toString).toList,
              ),
            )
          } @@ nonFlaky(10),
          test("streaming request") {
            val res      = ZIO
              .foreachPar((1 to N).toList) { idx =>
                val stream = ZStream.fromIterable(List("a", "b", "c-", idx.toString), chunkSize = 1)
                app
                  .deploy(
                    Request(
                      method = Method.POST,
                      body = Body.fromStream(stream),
                      headers = extraHeaders,
                    ),
                  )
                  .flatMap(_.body.asString)
              }
            val expected = (1 to N).map(idx => s"abc-$idx").toList
            assertZIO(res)(equalTo(expected))
          } @@ nonFlaky(10),
          test("streaming response") {
            val res =
              ZIO.foreachPar((1 to N).toList) { idx =>
                app
                  .deploy(
                    Request(
                      method = Method.POST,
                      url = URL.root / "streaming",
                      body = Body.fromString(idx.toString),
                      headers = extraHeaders,
                    ),
                  )
                  .flatMap(_.body.asString)
              }
            assertZIO(res)(
              equalTo(
                (1 to N).map(_.toString).toList,
              ),
            )
          } @@ nonFlaky(10),
          test("streaming request and response") {
            val res      = ZIO.foreachPar((1 to N).toList) { idx =>
              val stream = ZStream.fromIterable(List("a", "b", "c-", idx.toString), chunkSize = 1)
              app
                .deploy(
                  Request(
                    method = Method.POST,
                    url = URL.root / "streaming",
                    body = Body.fromStream(stream),
                    headers = extraHeaders,
                  ),
                )
                .flatMap(_.body.asString)
            }
            val expected = (1 to N).map(idx => s"abc-$idx").toList
            assertZIO(res)(equalTo(expected))
          } @@ nonFlaky(10),
          test("interrupting the parallel clients") {
            val res =
              ZIO.foreachPar(1 to 16) { idx =>
                app
                  .deploy(
                    Request(
                      method = Method.GET,
                      url = URL.root / "slow",
                      body = Body.fromString(idx.toString),
                      headers = extraHeaders,
                    ),
                  )
                  .flatMap(_.body.asString)
                  .fork
                  .flatMap { fib =>
                    fib.interrupt.unit.delay(500.millis)
                  }
              }
            assertZIO(res)(hasSize(equalTo(16)))
          },
          test("interrupting the sequential clients") {
            val res =
              // ZIO.scoped {
              ZIO.foreach(1 to 16) { idx =>
                app
                  .deploy(
                    Request(
                      method = Method.GET,
                      url = URL.root / "slow",
                      body = Body.fromString(idx.toString),
                      headers = extraHeaders,
                    ),
                  )
                  .flatMap(_.body.asString)
                  .fork
                  .flatMap { fib =>
                    fib.interrupt.unit.delay(100.millis)
                  }
              }
            // }
            assertZIO(res)(hasSize(equalTo(16)))
          },
        )
      },
    )

  def connectionPoolSpec: Spec[Any, Throwable] =
    suite("ConnectionPool")(
      suite("fixed")(
        connectionPoolTests(
          Version.Http_1_1,
          Map(
            "without connection close" -> Headers.empty,
            "with connection close"    -> connectionCloseHeader,
          ),
        ),
        connectionPoolTests(
          Version.Http_1_0,
          Map(
            "without keep-alive" -> Headers.empty,
            "with keep-alive"    -> keepAliveHeader,
          ),
        ),
      ).provide(
        ZLayer(appKeepAliveEnabled.unit),
        DynamicServer.live,
        severTestLayer,
        Client.customized,
        ZLayer.succeed(ZClient.Config.default.fixedConnectionPool(2)),
        NettyClientDriver.live,
        DnsResolver.default,
        ZLayer.succeed(NettyConfig.default),
        Scope.default,
      ),
      suite("dynamic")(
        connectionPoolTests(
          Version.Http_1_1,
          Map(
            "without connection close" -> Headers.empty,
            "with connection close"    -> connectionCloseHeader,
          ),
        ),
        connectionPoolTests(
          Version.Http_1_0,
          Map(
            "without keep-alive" -> Headers.empty,
            "with keep-alive"    -> keepAliveHeader,
          ),
        ),
      ).provideSome(
        ZLayer(appKeepAliveEnabled.unit),
        DynamicServer.live,
        severTestLayer,
        Client.customized,
        ZLayer.succeed(ZClient.Config.default.dynamicConnectionPool(4, 16, 100.millis)),
        NettyClientDriver.live,
        DnsResolver.default,
        ZLayer.succeed(NettyConfig.default),
        Scope.default,
      ),
    )

  override def spec: Spec[Scope, Throwable] = {
    connectionPoolSpec @@ timeout(30.seconds) @@ sequential @@ withLiveClock
  }

}
