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
import zio.test.TestAspect.{ignore, nonFlaky}
import zio.test.{Spec, TestAspect, TestEnvironment, assertZIO}

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

abstract class ClientHttpsSpecBase extends ZIOHttpSpec {

  private val zioDev =
    URL.decode("https://zio.dev").toOption.get

  private val badRequest =
    URL
      .decode(
        "https://httpbin.org/status/400",
      )
      .toOption
      .get

  private val untrusted =
    URL.decode("https://untrusted-root.badssl.com/").toOption.get

  def tests(sslConfig: ClientSSLConfig) = suite("Client")(
    test("respond Ok") {
      val actual = Client.batched(Request.get(zioDev))
      assertZIO(actual)(anything)
    }.provide(ZLayer.succeed(ZClient.Config.default), partialClientLayer),
    test("respond Ok with sslConfig") {
      val actual = Client.batched(Request.get(zioDev))
      assertZIO(actual)(anything)
    },
    test("should respond as Bad Request") {
      val actual = Client.batched(Request.get(badRequest)).map(_.status)
      assertZIO(actual)(equalTo(Status.BadRequest))
    } @@ ignore /* started getting 503 consistently,
    flaky does not help, nor exponential retries.
    Either we're being throttled, or the service is under high load.
    Regardless, we should not depend on an external service like that.
    Luckily, httpbin is available via docker.
    So once we make sure to:

    $ docker run -p 80:80 kennethreitz/httpbin

    before invoking tests, we can un-ignore this test. */,
    test("should throw DecoderException for handshake failure") {
      val actual = Client.batched(Request.get(untrusted)).exit
      assertZIO(actual)(
        fails(
          hasField(
            "class.simpleName",
            _.getClass.getSimpleName,
            isOneOf(List("DecoderException", "PrematureChannelClosureException")),
          ),
        ),
      )
    },
  )
    .provideShared(
      ZLayer.succeed(ZClient.Config.default.ssl(sslConfig)),
      partialClientLayer,
    ) @@ TestAspect.withLiveClock @@ TestAspect.flaky(5)

  private val partialClientLayer = ZLayer.makeSome[ZClient.Config, Client](
    Client.customized,
    NettyClientDriver.live,
    DnsResolver.default,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
  )
}

object ClientHttpsSpec extends ClientHttpsSpecBase {

  private val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  override def spec: Spec[TestEnvironment & Scope, Throwable] =
    suite("Https Client request - From Trust Store")(
      tests(sslConfig) @@ TestAspect.ignore,
    )
}

object ClientHttpsFromJavaxNetSslSpec extends ClientHttpsSpecBase {

  private val sslConfig =
    ClientSSLConfig.FromJavaxNetSsl
      .builderWithTrustManagerResource("trustStore.jks")
      .trustManagerPassword("changeit")
      .build()

  override def spec: Spec[TestEnvironment & Scope, Throwable] =
    suite("Https Client request - From Javax Net Ssl")(
      tests(sslConfig) @@ TestAspect.flaky(5) @@ TestAspect.ignore,
    )
}
