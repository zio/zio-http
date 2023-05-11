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
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.netty.NettyConfig

object NettyConnectionPoolSpec extends HttpRunnableSpec {

  private val app = Http.collectZIO[Request] {
    case req @ Method.POST -> Root / "streaming" => ZIO.succeed(Response(body = Body.fromStream(req.body.asStream)))
    case Method.GET -> Root / "slow"             => ZIO.sleep(1.hour).as(Response.text("done"))
    case req                                     =>
      req.body.asString.map(Response.text(_))
  }

  private val connectionCloseHeader = Headers(Header.Connection.Close)
  private val keepAliveHeader       = Headers(Header.Connection.KeepAlive)
  private val appKeepAliveEnabled   = serve(DynamicServer.app)

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
                app.deploy.body
                  .run(
                    method = Method.POST,
                    body = Body.fromString(idx.toString),
                    headers = extraHeaders,
                  )
                  .flatMap(_.asString)
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
                app.deploy.body
                  .run(
                    method = Method.POST,
                    body = Body.fromStream(stream),
                    headers = extraHeaders,
                  )
                  .flatMap(_.asString)
              }
            val expected = (1 to N).map(idx => s"abc-$idx").toList
            assertZIO(res)(equalTo(expected))
          } @@ nonFlaky(10),
          test("streaming response") {
            val res =
              ZIO.foreachPar((1 to N).toList) { idx =>
                app.deploy.body
                  .run(
                    method = Method.POST,
                    path = Root / "streaming",
                    body = Body.fromString(idx.toString),
                    headers = extraHeaders,
                  )
                  .flatMap(_.asString)
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
              app.deploy.body
                .run(
                  method = Method.POST,
                  path = Root / "streaming",
                  body = Body.fromStream(stream),
                  headers = extraHeaders,
                )
                .flatMap(_.asString)
            }
            val expected = (1 to N).map(idx => s"abc-$idx").toList
            assertZIO(res)(equalTo(expected))
          } @@ nonFlaky(10),
          test("interrupting the parallel clients") {
            val res =
              ZIO.foreachPar(1 to 16) { idx =>
                app.deploy.body
                  .run(
                    method = Method.GET,
                    path = Root / "slow",
                    body = Body.fromString(idx.toString),
                    headers = extraHeaders,
                  )
                  .flatMap(_.asString)
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
                app.deploy.body
                  .run(
                    method = Method.GET,
                    path = Root / "slow",
                    body = Body.fromString(idx.toString),
                    headers = extraHeaders,
                  )
                  .flatMap(_.asString)
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

  def connectionPoolSpec: Spec[Scope, Throwable] =
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
      ).provideSome[Scope](
        ZLayer(appKeepAliveEnabled.unit),
        DynamicServer.live,
        severTestLayer,
        Client.customized,
        ZLayer.succeed(ZClient.Config.default.withFixedConnectionPool(2)),
        NettyClientDriver.live,
        DnsResolver.default,
        ZLayer.succeed(NettyConfig.default),
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
      ).provideSome[Scope](
        ZLayer(appKeepAliveEnabled.unit),
        DynamicServer.live,
        severTestLayer,
        Client.customized,
        ZLayer.succeed(ZClient.Config.default.withDynamicConnectionPool(4, 16, 100.millis)),
        NettyClientDriver.live,
        DnsResolver.default,
        ZLayer.succeed(NettyConfig.default),
      ),
    )

  override def spec: Spec[Scope, Throwable] = {
    connectionPoolSpec @@ timeout(30.seconds) @@ sequential @@ withLiveClock
  }

}
