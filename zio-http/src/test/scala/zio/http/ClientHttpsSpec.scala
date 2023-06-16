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

import zio.test.Assertion.{anything, equalTo, fails, hasField}
import zio.test.TestAspect.{ignore, timeout}
import zio.test.{ZIOSpecDefault, assertZIO}
import zio.{Scope, ZLayer, durationInt}

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object ClientHttpsSpec extends ZIOSpecDefault {

  val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  override def spec = suite("Https Client request")(
    test("respond Ok") {
      val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics")
      assertZIO(actual)(anything)
    },
    test("respond Ok with sslConfig") {
      val actual = Client.request("https://sports.api.decathlon.com/groups/water-aerobics")
      assertZIO(actual)(anything)
    },
    test("should respond as Bad Request") {
      val actual = Client
        .request(
          "https://www.whatissslcertificate.com/google-has-made-the-list-of-untrusted-providers-of-digital-certificates/",
        )
        .map(_.status)
      assertZIO(actual)(equalTo(Status.BadRequest))
    },
    test("should throw DecoderException for handshake failure") {
      val actual = Client
        .request(
          "https://untrusted-root.badssl.com/",
        )
        .exit
      assertZIO(actual)(fails(hasField("class.simpleName", _.getClass.getSimpleName, equalTo("DecoderException"))))
    },
  ).provide(
    ZLayer.succeed(ZClient.Config.default.ssl(sslConfig)),
    Client.customized,
    NettyClientDriver.live,
    DnsResolver.default,
    ZLayer.succeed(NettyConfig.default),
    Scope.default,
  ) @@ timeout(
    30 seconds,
  ) @@ ignore
}
