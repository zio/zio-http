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

import scala.io.Source

import zio.ZLayer
import zio.test.Assertion.equalTo
import zio.test._

import zio.http.SSLConfig.HttpBehaviour
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object DualSSLSpec extends ZIOHttpSpec {

  private def loadResourceAsString(path: String): String =
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(path)).mkString

  private val clientSSL1 = ClientSSLConfig.FromCertResource("server.crt")
  private val clientSSL2 = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  private val clientSSLWithClientCert = ClientSSLConfig.FromClientAndServerCert(
    clientSSL1,
    ClientSSLCertConfig.FromClientCertResource("client.crt", "client.key"),
  )

  private val clientSSLWithClientCert2 = ClientSSLConfig.FromClientAndServerCert(
    clientSSL1,
    ClientSSLCertConfig.FromClientCertResource("client_other.crt", "client_other.key"),
  )

  private lazy val envClientCertContent: String = loadResourceAsString("client.crt")
  private lazy val envClientKeyContent: String  = loadResourceAsString("client.key")

  private val clientSSLWithEnvCertFromResource = ClientSSLConfig.FromClientAndServerCert(
    clientSSL1,
    ClientSSLCertConfig.FromClientCertContent(envClientCertContent, envClientKeyContent),
  )

  private val clientSSLWithInvalidEnvCert = ClientSSLConfig.FromClientAndServerCert(
    clientSSL1,
    ClientSSLCertConfig.FromClientCertContent("INVALID_CERT", "INVALID_KEY"),
  )

  private val sslConfigWithTrustedClient = SSLConfig.fromResource(
    HttpBehaviour.Redirect,
    "server.crt",
    "server.key",
    Some(ClientAuth.Required),
    Some("ca.pem"),
    includeClientCert = true,
  )

  private val config = Server.Config.default.port(8073).ssl(sslConfigWithTrustedClient).logWarningOnFatalError(false)

  private val payload = Gen.alphaNumericStringBounded(10000, 20000)

  private val routes: Routes[Any, Response] = Routes(
    Method.GET / "success" -> handler((req: Request) =>
      Response.text(
        req.remoteCertificate.map { _.asInstanceOf[X509Certificate].getSubjectX500Principal.getName() }.getOrElse(""),
      ),
    ),
  ).sandbox

  private val httpUrl =
    URL.decode("http://localhost:8073/success").toOption.get

  private val httpsUrl =
    URL.decode("https://localhost:8073/success").toOption.get

  private val successWithClientCert: Spec[Client, Throwable]#ZSpec[Any, Throwable, TestSuccess] =
    test("succeed when client has the server certificate and client certificate is configured") {
      val actual =
        Client.batched(Request.get(httpsUrl)).flatMap(r => r.body.asString.map(body => (r.status, body)))
      assertZIO(actual)(equalTo((Status.Ok, "O=client1,ST=Some-State,C=AU")))
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSLWithClientCert)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  // Unfortunately if the channel closes before we create the request, we can't extract the DecoderException
  private val failWithoutClientCert: Spec[Client, Nothing]#ZSpec[Any, Throwable, TestSuccess] =
    test("fail when client has the server certificate but no client certificate is configured") {
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
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  private val failWithWrongClientCert: Spec[Client, Nothing]#ZSpec[Any, Throwable, TestSuccess] =
    test("fail when client has the server certificate but wrong client certificate is configured") {
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
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSLWithClientCert2)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  private val successWithClientEnvCert =
    test("succeed when client cert and key are loaded from resource and passed as env stream") {
      val actual =
        Client.batched(Request.get(httpsUrl)).flatMap(r => r.body.asString.map(body => (r.status, body)))
      assertZIO(actual)(equalTo((Status.Ok, "O=client1,ST=Some-State,C=AU")))
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSLWithEnvCertFromResource)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  private val failWithInvalidClientEnvCert =
    test("fail when invalid client cert and key are passed from env string") {
      Client
        .batched(Request.get(httpsUrl))
        .fold(
          { e =>
            val expectedErrors =
              List("DecoderException", "PrematureChannelClosureException", "StacklessClosedChannelException")
            val errorType      = e.getClass.getSimpleName
            if (expectedErrors.contains(errorType)) assertCompletes
            else assertNever(s"unexpected error: $errorType")
          },
          _ => assertNever("expected failure"),
        )
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSLWithInvalidEnvCert)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  override def spec: Spec[Any, Throwable] = suite("SSL")(
    Server
      .installRoutes(routes)
      .as(
        List(
          successWithClientCert,
          failWithoutClientCert,
          failWithWrongClientCert,
          successWithClientEnvCert,
          failWithInvalidClientEnvCert,
        ),
      ),
  ).provideShared(
    Server.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    ZLayer.succeed(config),
  )

}
