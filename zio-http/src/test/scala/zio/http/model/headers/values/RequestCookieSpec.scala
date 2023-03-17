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

import zio.http.model
import zio.http.model.Cookie.Type.RequestType

object RequestCookieSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("RequestCookie suite")(
    test("RequestCookie handle valid cookie") {
      val result = RequestCookie.parse("foo=bar") match {
        case Right(RequestCookie(value)) =>
          value == List(model.Cookie(name = "foo", content = "bar", target = RequestType))
        case _                           => false
      }
      assertTrue(result)
    },
    test("RequestCookie handle invalid cookie") {
      val result = RequestCookie.parse("") match {
        case Right(RequestCookie(_)) =>
          false
        case _                       => true
      }
      assertTrue(result)
    },
    test("RequestCookie handle multiple cookies") {
      val result = RequestCookie.parse("foo=bar; foo2=bar2") match {
        case Right(RequestCookie(value)) =>
          value == List(
            model.Cookie(name = "foo", content = "bar", target = RequestType),
            model.Cookie(name = "foo2", content = "bar2", target = RequestType),
          )
        case _                           => false
      }
      assertTrue(result)
    },
    test("RequestCookie render valid cookie") {
      val result = RequestCookie.parse("foo=bar") match {
        case Right(rc) =>
          RequestCookie.render(rc) == "foo=bar"
        case _         => false
      }
      assertTrue(result)
    },
  )
}
