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

import zio.Config.Secret
import zio.ZLayer
import zio.test.Assertion.equalTo
import zio.test.{Gen, assertCompletes, assertNever, assertZIO}

import zio.http.SSLConfig.Data.TrustManagerKeyStore
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object ServerClientJKSMutualSSLSpec extends ZIOHttpSpec {

  val serverJKSKeyStoreWithPass   = "jks_keystore_truststore/server_keystore_with_pass.jks"
  val serverJKSTrustStoreWithPass = "jks_keystore_truststore/server_truststore_with_pass.jks"
  val password                    = Secret("123456")

  val serverSSLNettyConfig = SSLConfig.fromJavaxNetSslKeyStoreResource(
    keyManagerResource = serverJKSKeyStoreWithPass,
    keyManagerPassword = Some(password),
    trustManagerKeyStore = Some(
      TrustManagerKeyStore
        .fromResource(serverJKSTrustStoreWithPass)
        .build()
        .trustManagerPassword(password),
    ),
    // strict mutual tls -- server accepts only client certificates in its truststore
    clientAuth = Some(ClientAuth.Required),
  )
  val config               = Server.Config.default.port(8073).ssl(serverSSLNettyConfig).logWarningOnFatalError(false)

  // client that is mutually trusted by specific server
  val clientJKSKeyStoreWithPass   = "jks_keystore_truststore/client_keystore_with_pass.jks"
  val clientJKSTrustStoreWithPass = "jks_keystore_truststore/client_truststore_with_pass.jks"
  val clientSSL1                  = ClientSSLConfig
    .FromJavaxNetSsl()
    .keyManagerResource(clientJKSKeyStoreWithPass)
    .keyManagerPassword(password)
    .trustManagerResource(clientJKSTrustStoreWithPass)
    .trustManagerPassword(password)
  // client that has another certificate
  val clientSSL2                  = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  val payload = Gen.alphaNumericStringBounded(10000, 20000)

  val routes: Routes[Any, Response] = Routes(
    Method.GET / "success" -> handler(Response.ok),
    Method.GET / "file"    -> Handler.fromResource("TestStatic/TestFile1.txt"),
  ).sandbox

  val httpUrl =
    URL.decode("http://localhost:8073/success").toOption.get

  val httpsUrl =
    URL.decode("https://localhost:8073/success").toOption.get

  val staticFileUrl =
    URL.decode("https://localhost:8073/file").toOption.get

  override def spec =
    suite("Server KeyStore SSL")(
      Server
        .installRoutes(routes)
        .as(
          List(
            test("succeed when client has the server certificate and trusted by the server - mutual tls") {
              val actual = Client.batched(Request.get(httpsUrl)).map(_.status)
              assertZIO(actual)(equalTo(Status.Ok))
            }.provide(
              Client.customized,
              ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
              NettyClientDriver.live,
              DnsResolver.default,
              ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            ),
            test(
              "fail with DecoderException or PrematureChannelClosureException with Default clients not in server's truststore",
            ) {
              Client
                .batched(Request.get(httpsUrl))
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
              ZLayer.succeed(ZClient.Config.default.ssl(ClientSSLConfig.Default)),
              NettyClientDriver.live,
              DnsResolver.default,
              ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            ),
            // Unfortunately if the channel closes before we create the request, we can't extract the DecoderException
            test(
              "fail with DecoderException or PrematureChannelClosureException when client doesn't have the server certificate",
            ) {
              Client
                .batched(Request.get(httpsUrl))
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
            ),
            test("Https Redirect when client makes http request") {
              val actual = Client.batched(Request.get(httpUrl)).map(_.status)
              assertZIO(actual)(equalTo(Status.PermanentRedirect))
            }.provide(
              Client.customized,
              ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
              NettyClientDriver.live,
              DnsResolver.default,
              ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            ),
          ),
        ),
    ).provideShared(
      ZLayer.fromFunction((c: Server.Config) => ServerRuntimeConfig(c)),
      Server.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      ZLayer.succeed(config),
    )

}
