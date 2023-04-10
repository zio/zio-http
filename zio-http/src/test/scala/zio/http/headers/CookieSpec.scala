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

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{NonEmptyChunk, Scope}

import zio.http.Header.Cookie

object CookieSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Cookie suite")(
    test("Cookie handle valid cookie") {
      val result = Cookie.parse("foo=bar") match {
        case Right(Cookie(value)) =>
          value == NonEmptyChunk(zio.http.Cookie.Request(name = "foo", content = "bar"))
        case _                    => false
      }
      assertTrue(result)
    },
    test("Cookie handle invalid cookie") {
      val result = Cookie.parse("") match {
        case Right(Cookie(_)) =>
          false
        case _                => true
      }
      assertTrue(result)
    },
    test("Cookie handle multiple cookies") {
      val result = Cookie.parse("foo=bar; foo2=bar2") match {
        case Right(Cookie(value)) =>
          value == NonEmptyChunk(
            zio.http.Cookie.Request(name = "foo", content = "bar"),
            zio.http.Cookie.Request(name = "foo2", content = "bar2"),
          )
        case _                    => false
      }
      assertTrue(result)
    },
    test("Cookie render valid cookie") {
      val result = Cookie.parse("foo=bar") match {
        case Right(rc) =>
          Cookie.render(rc) == "foo=bar"
        case _         => false
      }
      assertTrue(result)
    },
  )
}
