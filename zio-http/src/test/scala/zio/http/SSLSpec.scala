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

import zio.test.Assertion.equalTo
import zio.test.TestAspect.{ignore, timeout}
import zio.test.{Gen, ZIOSpecDefault, assertZIO, check}
import zio.{Scope, ZIO, durationInt}

import zio.http._
import zio.http.model._
import zio.http.netty.client.{NettyClientDriver, NettyConnectionPool}

import io.netty.handler.codec.DecoderException

object SSLSpec extends ZIOSpecDefault {

  val sslConfig = SSLConfig.fromResource("server.crt", "server.key")
  val config    = ServerConfig.default.port(8073).ssl(sslConfig)

  val clientSSL1 = ClientSSLConfig.FromCertResource("server.crt")
  val clientSSL2 = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  val payload = Gen.alphaNumericStringBounded(10000, 20000)

  val app: HttpApp[Any, Throwable] = Http.collectZIO[Request] {
    case Method.GET -> !! / "success"     =>
      ZIO.succeed(Response.ok)
    case req @ Method.POST -> !! / "text" =>
      for {
        body <- req.body.asString
      } yield Response.text(body)
  }

  override def spec = suite("SSL")(
    Server
      .serve(app.withDefaultErrorResponse)
      .as(
        List(
          test("succeed when client has the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success")
              .map(_.status)
            assertZIO(actual)(equalTo(Status.Ok))
          }.provide(
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL1)),
            NettyClientDriver.fromConfig,
            DnsResolver.default,
          ),
          test("fail with DecoderException when client doesn't have the server certificate") {
            val actual = Client
              .request("https://localhost:8073/success")
              .catchSome { case _: DecoderException =>
                ZIO.succeed("DecoderException")
              }
            assertZIO(actual)(equalTo("DecoderException"))
          }.provide(
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL2)),
            NettyClientDriver.fromConfig,
            DnsResolver.default,
          ),
          test("succeed when client has default SSL") {
            val actual = Client
              .request("https://localhost:8073/success")
              .map(_.status)
            assertZIO(actual)(equalTo(Status.Ok))
          }.provide(
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(ClientSSLConfig.Default)),
            NettyClientDriver.fromConfig,
            DnsResolver.default,
          ),
          test("Https Redirect when client makes http request") {
            val actual = Client
              .request("http://localhost:8073/success")
              .map(_.status)
            assertZIO(actual)(equalTo(Status.PermanentRedirect))
          }.provide(
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL1)),
            NettyClientDriver.fromConfig,
            DnsResolver.default,
          ),
          test("Https request with a large payload should respond with 413") {
            check(payload) { payload =>
              val actual = Client
                .request(
                  "https://localhost:8073/text",
                  Method.POST,
                  content = Body.fromString(payload),
                )
                .map(_.status)
              assertZIO(actual)(equalTo(Status.RequestEntityTooLarge))
            }
          }.provide(
            Client.live,
            ClientConfig.live(ClientConfig.empty.ssl(clientSSL1)),
            NettyClientDriver.fromConfig,
            DnsResolver.default,
          ),
        ),
      ),
  ).provideShared(
    ServerConfig.live(config),
    Server.live,
  ) @@
    timeout(5 second) @@ ignore

}
