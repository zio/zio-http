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

import zio.http.Header.SetCookie
import zio.http.ZIOHttpSpec

object SetCookieSpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SetCookieSpec suite")(
    test("SetCookie handle valid cookie") {
      val result = SetCookie.parse("foo=bar") match {
        case Right(SetCookie(value)) =>
          value.name == "foo" && value.content == "bar"
        case _                       => false
      }
      assertTrue(result)
    },
    test("SetCookie handle invalid cookie") {
      val result = SetCookie.parse("") match {
        case Right(SetCookie(_)) =>
          false
        case _                   => true
      }
      assertTrue(result)
    },
    test("SetCookie render valid cookie") {
      val result = SetCookie.parse("foo=bar") match {
        case Right(rc) =>
          SetCookie.render(rc) == "foo=bar"
        case _         => false
      }
      assertTrue(result)
    },
  )
}
