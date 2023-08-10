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

package zio.http.headers

import zio.Scope
import zio.test._

import zio.http.Header.Connection
import zio.http.ZIOHttpSpec
import zio.http.internal.HttpGen

object ConnectionSpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Connection header suite")(
    test("connection header transformation must be symmetrical") {
      check(HttpGen.connectionHeader) { connectionHeader =>
        assertTrue(Connection.parse(Connection.render(connectionHeader)) == Right(connectionHeader))
      }
    },
    test("invalid connection header value should be parsed to an empty string") {
      assertTrue(Connection.parse("").isLeft)
    },
    test("invalid values parsing") {
      check(Gen.stringBounded(20, 25)(Gen.char)) { value =>
        assertTrue(Connection.parse(value).isLeft)
      }
    },
  )
}
