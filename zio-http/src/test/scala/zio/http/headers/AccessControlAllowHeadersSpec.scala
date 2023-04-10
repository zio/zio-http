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
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

import zio.http.Header.AccessControlAllowHeaders
import zio.http.internal.HttpGen

object AccessControlAllowHeadersSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlAllowHeaders suite")(
    test("AccessControlAllowHeaders should be parsed correctly for *") {
      assertTrue(
        AccessControlAllowHeaders.parse("*") == Right(AccessControlAllowHeaders.All),
      )
    },
    test("AccessControlAllowHeaders should be rendered correctly for *") {
      assertTrue(
        AccessControlAllowHeaders.render(AccessControlAllowHeaders.All) == "*",
      )
    },
    test("AccessControlAllowHeaders should be parsed correctly for valid header names") {
      check(HttpGen.headerNames1) { headerNames =>
        val headerNamesString = headerNames.mkString(", ")
        if (headerNamesString.isEmpty)
          assertTrue(
            AccessControlAllowHeaders.parse(
              headerNamesString,
            ) == Right(AccessControlAllowHeaders.None),
          )
        else
          assertTrue(
            AccessControlAllowHeaders.parse(headerNamesString) == Right(
              AccessControlAllowHeaders
                .Some(headerNames),
            ),
          )
      }
    },
  )
}
