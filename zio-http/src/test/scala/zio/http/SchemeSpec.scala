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

import zio.test.Assertion.isNone
import zio.test._

import zio.http.internal.HttpGen

object SchemeSpec extends ZIOHttpSpec {
  override def spec = suite("SchemeSpec")(
    test("string decode") {
      checkAll(HttpGen.scheme) { scheme =>
        assertTrue(Scheme.decode(scheme.encode).get == scheme)
      }
    },
    test("null string decode") {
      assert(Scheme.decode(null))(isNone)
    },
    test("decode chrome-extension") {
      assertTrue(Scheme.decode("chrome-extension").isDefined)
    },
  )
}
