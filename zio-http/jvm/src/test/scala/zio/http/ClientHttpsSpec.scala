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
import zio.test.{TestAspect, assertZIO}

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object ClientHttpsSpec extends ZIOHttpSpec {

  val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  val zioDev =
    URL.decode("https://zio.dev").toOption.get

  val badRequest =
    URL
      .decode(
        "https://www.whatissslcertificate.com/google-has-made-the-list-of-untrusted-providers-of-digital-certificates/",
      )
      .toOption
      .get

  val untrusted =
    URL.decode("https://untrusted-root.badssl.com/").toOption.get

  override def spec = suite("Https Client request")(
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
    } @@ ignore,
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
    } @@ nonFlaky(20) @@ ignore,
  )
    .provideShared(
      ZLayer.succeed(ZClient.Config.default.ssl(sslConfig)),
      partialClientLayer,
    ) @@ TestAspect.withLiveClock

  private val partialClientLayer = ZLayer.makeSome[ZClient.Config, Client](
    Client.customized,
    NettyClientDriver.live,
    DnsResolver.default,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
  )
}
