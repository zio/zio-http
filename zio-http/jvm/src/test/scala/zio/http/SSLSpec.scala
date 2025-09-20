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

import scala.io.Source

import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.withLiveClock
import zio.test.{Gen, assertCompletes, assertNever, assertZIO}

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object SSLSpec extends ZIOHttpSpec {

  private def loadResourceAsString(path: String): String =
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(path)).mkString

  private val sslConfig = SSLConfig.fromResource("server.crt", "server.key")
  private val config    = Server.Config.default.port(8073).ssl(sslConfig).logWarningOnFatalError(false)

  private val clientSSL1 = ClientSSLConfig.FromCertResource("server.crt")
  private val clientSSL2 = ClientSSLConfig.FromCertResource("ss2.crt.pem")

  private val payload = Gen.alphaNumericStringBounded(10000, 20000)

  private val routes: Routes[Any, Response] = Routes(
    Method.GET / "success" -> handler(Response.ok),
    Method.GET / "file"    -> Handler.fromResource("TestStatic/TestFile1.txt"),
  ).sandbox

  private val httpUrl =
    URL.decode("http://localhost:8073/success").toOption.get

  private val httpsUrl =
    URL.decode("https://localhost:8073/success").toOption.get

  private val staticFileUrl =
    URL.decode("https://localhost:8073/file").toOption.get

  private lazy val clientCertFromResource: String = loadResourceAsString("client.crt")
  private lazy val clientKeyFromResource: String  = loadResourceAsString("client.key")

  private lazy val clientSSLWithEnvContent = ClientSSLConfig.FromClientAndServerCert(
    clientSSL1,
    ClientSSLCertConfig.FromClientCertContent(clientCertFromResource, clientKeyFromResource),
  )

  private val successWithClientCert =
    test("succeed when client has the server certificate") {
      val actual = Client.batched(Request.get(httpsUrl)).map(_.status)
      assertZIO(actual)(equalTo(Status.Ok))
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  // Unfortunately if the channel closes before we create the request, we can't extract the DecoderException
  private val failWithoutClientCert =
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
    )

  private val successWithDefaultClientCert =
    test("succeed when client has default SSL") {
      val actual = Client.batched(Request.get(httpsUrl)).map(_.status)
      assertZIO(actual)(equalTo(Status.Ok))
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(ClientSSLConfig.Default)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  private val successOnHttpsRedirect =
    test("Https Redirect when client makes http request") {
      val actual = Client.batched(Request.get(httpUrl)).map(_.status)
      assertZIO(actual)(equalTo(Status.PermanentRedirect))
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSL1)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  private val staticFiles =
    test("static files") {
      val actual = Client.batched(Request.get(staticFileUrl)).flatMap(_.body.asString)
      assertZIO(actual)(equalTo("This file is added for testing Static File Server."))
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(ClientSSLConfig.Default)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    ) @@ withLiveClock

  private val successWithClientEnvCert =
    test("succeed when client uses cert/key passed as strings from env (simulated via resource)") {
      val actual = Client.batched(Request.get(httpsUrl)).map(_.status)
      assertZIO(actual)(equalTo(Status.Ok))
    }.provide(
      Client.customized,
      ZLayer.succeed(ZClient.Config.default.ssl(clientSSLWithEnvContent)),
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    )

  override def spec = suite("SSL")(
    Server
      .installRoutes(routes)
      .as(
        List(
          successWithClientCert,
          failWithoutClientCert,
          successWithDefaultClientCert,
          successOnHttpsRedirect,
          staticFiles,
          successWithClientEnvCert,
        ),
      ),
  ).provideShared(
    Server.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    ZLayer.succeed(config),
  )

}
