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
import zio.test.{Spec, TestEnvironment, assertTrue}

import zio.http.Header.AccessControlAllowCredentials
import zio.http.ZIOHttpSpec

object AccessControlAllowCredentialsSpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlAllowCredentials suite")(
    test("AccessControlAllowCredentials should be parsed correctly for true") {
      assertTrue(
        AccessControlAllowCredentials.parse(
          "true",
        ) == Right(AccessControlAllowCredentials.Allow),
      )
    },
    test("AccessControlAllowCredentials should be parsed correctly for false") {
      assertTrue(
        AccessControlAllowCredentials.parse(
          "false",
        ) == Right(AccessControlAllowCredentials.DoNotAllow),
      )
    },
    test("AccessControlAllowCredentials should be parsed correctly for invalid string") {
      assertTrue(
        AccessControlAllowCredentials.parse(
          "some dummy string",
        ) == Right(AccessControlAllowCredentials.DoNotAllow),
      )
    },
    test("AccessControlAllowCredentials should be parsed correctly for empty string") {
      assertTrue(
        AccessControlAllowCredentials.parse(
          "",
        ) == Right(AccessControlAllowCredentials.DoNotAllow),
      )
    },
    test("AccessControlAllowCredentials should be rendered correctly to false") {
      assertTrue(
        AccessControlAllowCredentials.render(
          AccessControlAllowCredentials.DoNotAllow,
        ) == "false",
      )
    },
    test("AccessControlAllowCredentials should be rendered correctly to true") {
      assertTrue(
        AccessControlAllowCredentials.render(
          AccessControlAllowCredentials.Allow,
        ) == "true",
      )
    },
  )
}
