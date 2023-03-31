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

package zio.http.netty

import zio.test.Assertion.{equalTo, isNone, isNull, isSome}
import zio.test._

import zio.http.middleware.Auth.Credentials
import zio.http.{Proxy, URL}

object NettyProxySpec extends ZIOSpecDefault {
  private val validUrl = URL.decode("http://localhost:8123").toOption.getOrElse(URL.empty)

  override def spec = suite("Proxy")(
    suite("Authenticated Proxy")(
      test("successfully encode valid proxy") {
        val username = "unameTest"
        val password = "upassTest"
        val proxy    = Proxy(validUrl, Some(Credentials(username, password)))
        val encoded  = NettyProxy.fromProxy(proxy).encode

        assert(encoded.map(_.username()))(isSome(equalTo(username))) &&
        assert(encoded.map(_.password()))(isSome(equalTo(password))) &&
        assert(encoded.map(_.authScheme()))(isSome(equalTo("basic")))
      },
      test("fail to encode invalid proxy") {
        val proxy   = Proxy(URL.empty)
        val encoded = NettyProxy.fromProxy(proxy).encode

        assert(encoded.map(_.username()))(isNone)
      },
    ),
    suite("Unauthenticated proxy")(
      test("successfully encode valid proxy") {
        val proxy   = Proxy(validUrl)
        val encoded = NettyProxy.fromProxy(proxy).encode

        assert(encoded)(isSome) &&
        assert(encoded.map(_.username()))(isSome(isNull)) &&
        assert(encoded.map(_.password()))(isSome(isNull)) &&
        assert(encoded.map(_.authScheme()))(isSome(equalTo("none")))
      },
    ),
  )
}
