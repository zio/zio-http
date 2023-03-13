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
import zio.{Chunk, Scope}

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Allow._

object AllowSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Allow header suite")(
    test("allow header transformation must be symmetrical") {
      check(HttpGen.allowHeader) { allowHeader =>
        assertTrue(Allow.toAllow(Allow.fromAllow(allowHeader)) == allowHeader)
      }
    },
    test("empty header value should be parsed to an empty chunk") {
      assertTrue(Allow.toAllow("") == AllowMethods(Chunk.empty))
    },
    test("invalid values parsing") {
      check(Gen.stringBounded(10, 15)(Gen.char)) { value =>
        assertTrue(Allow.toAllow(value) == AllowMethods(Chunk(InvalidAllowMethod)))
      }
    },
  )
}
