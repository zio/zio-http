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
import zio.test.TestAspect.withLiveClock
import zio.test.{Gen, assertCompletes, assertNever, assertZIO}
import zio.{Scope, ZLayer}

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object SSLSpec extends ZIOHttpSpec {

  val sslConfig = SSLConfig.fromResource("server.crt", "server.key")
  val config    = Server.Config.default.port(8073).ssl(sslConfig)

  val clientSSL1 = ClientSSLConfig.FromCertResource("server.crt")
  val clientSSL2 = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  val payload = Gen.alphaNumericStringBounded(10000, 20000)

  val app: Routes[Any, Response] = Routes(
    Method.GET / "success" -> handler(Response.ok),
    Method.GET / "file"    -> Handler.fromResource("TestStatic/TestFile1.txt"),
  ).sandbox

  val httpUrl =
    URL.decode("http://localhost:8073/success").toOption.get

  val httpsUrl =
    URL.decode("https://localhost:8073/success").toOption.get

  val staticFileUrl =
    URL.decode("https://localhost:8073/file").toOption.get

  override def spec = suite("SSL")(
    Server
      .install(app)
      .as(
        List(
          test("succeed when client has the server certificate") {
            val actual = Client
              .request(Request.get(httpsUrl))
              .map(_.status)
            assertZIO(actual)(equalTo(Status.Ok))
          }.provide(
            Client.customized,
            ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ),
          // Unfortunately if the channel closes before we create the request, we can't extract the DecoderException
          test(
            "fail with DecoderException or PrematureChannelClosureException when client doesn't have the server certificate",
          ) {
            Client
              .request(Request.get(httpsUrl))
              .fold(
                { e =>
                  val expectedErrors = List("DecoderException", "PrematureChannelClosureException")
                  val errorType      = e.getClass.getSimpleName
                  if (expectedErrors.contains(errorType)) assertCompletes
                  else assertNever(s"request failed with unexpected error type: $errorType")
                },
                _ => assertNever("expected request to fail"),
              )
          }.provide(
            Client.customized,
            ZLayer.succeed(ZClient.Config.default.ssl(clientSSL2)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ),
          test("succeed when client has default SSL") {
            val actual = Client
              .request(Request.get(httpsUrl))
              .map(_.status)
            assertZIO(actual)(equalTo(Status.Ok))
          }.provide(
            Client.customized,
            ZLayer.succeed(ZClient.Config.default.ssl(ClientSSLConfig.Default)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ),
          test("Https Redirect when client makes http request") {
            val actual = Client
              .request(Request.get(httpUrl))
              .map(_.status)
            assertZIO(actual)(equalTo(Status.PermanentRedirect))
          }.provide(
            Client.customized,
            ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ),
          test("static files") {
            val actual = Client
              .request(Request.get(staticFileUrl))
              .flatMap(_.body.asString)
            assertZIO(actual)(equalTo("This file is added for testing Static File Server."))
          }.provide(
            Client.customized,
            ZLayer.succeed(ZClient.Config.default.ssl(ClientSSLConfig.Default)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ) @@ withLiveClock,
        ),
      ),
  ).provideShared(
    Server.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    ZLayer.succeed(config),
  )

}
