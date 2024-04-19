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

import java.security.cert.X509Certificate

import zio.test.Assertion.equalTo
import zio.test.{Gen, assertCompletes, assertNever, assertZIO}
import zio.{Scope, ZLayer}

import zio.http.SSLConfig.HttpBehaviour
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object DualSSLSpec extends ZIOHttpSpec {

  val clientSSL1 = ClientSSLConfig.FromCertResource("server.crt")
  val clientSSL2 = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  val clientSSLWithClientCert = ClientSSLConfig.FromClientAndServerCert(
    clientSSL1,
    ClientSSLConfig.FromClientCertResource("client.crt", "client.key"),
  )

  val clientSSLWithClientCert2 = ClientSSLConfig.FromClientAndServerCert(
    clientSSL1,
    ClientSSLConfig.FromClientCertResource("client_other.crt", "client_other.key"),
  )

  val sslConfigWithTrustedClient = SSLConfig.fromResource(
    HttpBehaviour.Redirect,
    "server.crt",
    "server.key",
    Some(ClientAuth.Required),
    Some("ca.pem"),
    includeClientCert = true,
  )

  val config = Server.Config.default.port(8073).ssl(sslConfigWithTrustedClient)

  val payload = Gen.alphaNumericStringBounded(10000, 20000)

  val app: HttpApp[Any] = Routes(
    Method.GET / "success" -> handler((req: Request) =>
      Response.text(
        req.remoteCertificate.map { _.asInstanceOf[X509Certificate].getSubjectX500Principal.getName() }.getOrElse(""),
      ),
    ),
  ).sandbox.toHttpApp

  val httpUrl =
    URL.decode("http://localhost:8073/success").toOption.get

  val httpsUrl =
    URL.decode("https://localhost:8073/success").toOption.get

  override def spec = suite("SSL")(
    Server
      .install(app)
      .as(
        List(
          test("succeed when client has the server certificate and client certificate is configured") {
            val actual = Client
              .request(Request.get(httpsUrl))
              .flatMap(r => r.body.asString.map(body => (r.status, body)))
            assertZIO(actual)(equalTo((Status.Ok, "O=client1,ST=Some-State,C=AU")))
          }.provide(
            Client.customized,
            ZLayer.succeed(ZClient.Config.default.ssl(clientSSLWithClientCert)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ),
          // Unfortunately if the channel closes before we create the request, we can't extract the DecoderException
          test("fail when client has the server certificate but no client certificate is configured") {
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
            ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ),
          test("fail when client has the server certificate but wrong client certificate is configured") {
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
            ZLayer.succeed(ZClient.Config.default.ssl(clientSSLWithClientCert2)),
            NettyClientDriver.live,
            DnsResolver.default,
            ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            Scope.default,
          ),
        ),
      ),
  ).provideShared(
    Server.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    ZLayer.succeed(config),
  )

}
