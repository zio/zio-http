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

import zio.Scope
import zio.test._

import zio.http.internal.HttpGen

object HostSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Host header suite")(
      test("Empty Host") {
        assertTrue(Host.parse("").isLeft)
      },
      test("parsing of valid Host values") {
        check(HttpGen.genAbsoluteLocation) { url =>
          assertTrue(Host.parse(url.host) == Right(Host(url.host))) &&
          assertTrue(Host.parse(s"${url.host}:${url.port}") == Right(Host(url.host, url.port)))
        }
      },
      test("parsing of invalid Host values") {
        assertTrue(Host.parse("random.com:ds43").isLeft) &&
        assertTrue(Host.parse("random.com:ds43:4434").isLeft)

      },
      test("parsing and encoding is symmetrical") {
        check(HttpGen.genAbsoluteLocation) { url =>
          assertTrue(Host.render(Host.parse(url.host).toOption.get) == url.host) &&
          assertTrue(Host.render(Host.parse(s"${url.host}:${url.port}").toOption.get) == s"${url.host}:${url.port}")

        }
      },
    )
}
