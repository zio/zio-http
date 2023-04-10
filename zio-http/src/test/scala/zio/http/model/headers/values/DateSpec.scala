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
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.Header.Date

object DateSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Date suite")(
    suite("Date header value transformation should be symmetrical")(
      test("Date rendering should be reversible") {
        val value = "Wed, 21 Oct 2015 07:28:00 GMT"
        assertTrue(Date.render(Date.parse(value).toOption.get) == value)
      },
      test("Date parsing should fail for invalid date") {
        val value = "Wed, 21 Oct 20 07:28:00"
        assertTrue(Date.parse(value).isLeft)
      },
      test("Date parsing should fail for empty date") {
        val value = ""
        assertTrue(Date.parse(value).isLeft)
      },
    ),
  )
}
