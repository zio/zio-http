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

object ExpectSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Expect header suite")(
      test("parse valid value") {
        assertTrue(Expect.toExpect("100-continue") == Expect.ExpectValue) &&
        assertTrue(Expect.fromExpect(Expect.ExpectValue) == "100-continue")
      },
      test("parse invalid value") {
        assertTrue(Expect.toExpect("") == Expect.InvalidExpectValue) &&
        assertTrue(Expect.toExpect("200-ok") == Expect.InvalidExpectValue) &&
        assertTrue(Expect.fromExpect(Expect.InvalidExpectValue).isEmpty)
      },
    )
}
