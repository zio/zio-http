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
import zio.test.{Spec, TestAspect, TestEnvironment, assertZIO}

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

abstract class ClientHttpsSpecBase extends ZIOHttpSpec {

  val sslConfig = SSLConfig.fromResource("server.crt", "server.key")
  val config    = Server.Config.default.port(0).ssl(sslConfig).logWarningOnFatalError(false)

  val routes: Routes[Any, Response] = Routes(
    Method.GET / "success" -> handler(Response.ok),
    Method.GET / "bad"     -> handler(Response(status = Status.BadRequest)),
  ).sandbox

  def trustedClientSSLConfig: ClientSSLConfig
  def untrustedClientSSLConfig: ClientSSLConfig

  private val partialClientLayer = ZLayer.makeSome[ZClient.Config, Client](
    Client.customized,
    NettyClientDriver.live,
    DnsResolver.default,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
  )

  def tests = suite("Client")(
    Server
      .installRoutes(routes)
      .flatMap { port =>
        val httpsUrl = URL.decode(s"https://localhost:$port/success").toOption.get
        val badUrl   = URL.decode(s"https://localhost:$port/bad").toOption.get

        ZIO.succeed(
          List(
            test("respond Ok") {
              val actual = Client.batched(Request.get(httpsUrl)).map(_.status)
              assertZIO(actual)(equalTo(Status.Ok))
            }.provide(
              ZLayer.succeed(ZClient.Config.default.ssl(trustedClientSSLConfig)),
              partialClientLayer,
            ),
            test("should respond as Bad Request") {
              val actual = Client.batched(Request.get(badUrl)).map(_.status)
              assertZIO(actual)(equalTo(Status.BadRequest))
            }.provide(
              ZLayer.succeed(ZClient.Config.default.ssl(trustedClientSSLConfig)),
              partialClientLayer,
            ),
            test("should throw DecoderException for handshake failure") {
              Client
                .batched(Request.get(httpsUrl))
                .fold(
                  { e =>
                    val expectedErrors = List("DecoderException", "PrematureChannelClosureException")
                    val errorType      = e.getClass.getSimpleName
                    if (expectedErrors.contains(errorType)) zio.test.assertCompletes
                    else zio.test.assertNever(s"request failed with unexpected error type: $errorType")
                  },
                  _ => zio.test.assertNever("expected request to fail"),
                )
            }.provide(
              ZLayer.succeed(ZClient.Config.default.ssl(untrustedClientSSLConfig)),
              partialClientLayer,
            ),
          ),
        )
      },
  ).provideShared(
    Server.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    ZLayer.succeed(config),
  ) @@ TestAspect.withLiveClock
}

object ClientHttpsSpec extends ClientHttpsSpecBase {

  override val trustedClientSSLConfig: ClientSSLConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "server-truststore.jks",
    trustStorePassword = "changeit",
  )

  override val untrustedClientSSLConfig: ClientSSLConfig = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  override def spec: Spec[TestEnvironment & Scope, Throwable] =
    suite("Https Client request - From Trust Store")(
      tests,
    )
}

object ClientHttpsFromJavaxNetSslSpec extends ClientHttpsSpecBase {

  override val trustedClientSSLConfig: ClientSSLConfig =
    ClientSSLConfig.FromJavaxNetSsl
      .builderWithTrustManagerResource("server-truststore.jks")
      .trustManagerPassword("changeit")
      .build()

  override val untrustedClientSSLConfig: ClientSSLConfig = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  override def spec: Spec[TestEnvironment & Scope, Throwable] =
    suite("Https Client request - From Javax Net Ssl")(
      tests,
    )
}
