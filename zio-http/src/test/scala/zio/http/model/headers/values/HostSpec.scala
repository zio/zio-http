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

package zio.http.model.headers.values

import zio.test._

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Host.{HostValue, InvalidHostValue}

object HostSpec extends ZIOSpecDefault {
  override def spec = suite("Host header suite")(
    test("Empty Host") {
      assertTrue(Host.toHost("") == Host.EmptyHostValue) &&
      assertTrue(Host.fromHost(Host.EmptyHostValue) == "")
    },
    test("parsing of valid Host values") {
      check(HttpGen.genAbsoluteLocation) { url =>
        assertTrue(Host.toHost(url.host) == HostValue(url.host))
        assertTrue(Host.toHost(s"${url.host}:${url.port}") == HostValue(url.host, url.port))
      }
    },
    test("parsing of invalid Host values") {
      assertTrue(Host.toHost("random.com:ds43") == InvalidHostValue)
      assertTrue(Host.toHost("random.com:ds43:4434") == InvalidHostValue)

    },
    test("parsing and encoding is symmetrical") {
      check(HttpGen.genAbsoluteLocation) { url =>
        assertTrue(Host.fromHost(Host.toHost("random.com:4ds")) == "")
        assertTrue(Host.fromHost(Host.toHost("")) == "")
        assertTrue(Host.fromHost(Host.toHost(url.host)) == url.host)
        assertTrue(Host.fromHost(Host.toHost(s"${url.host}:${url.port}")) == s"${url.host}:${url.port}")

      }
    },
  )
}
