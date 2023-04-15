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

import zio.Scope
import zio.test._

object RequestSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("Result")(
    test("`#default`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.POST,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.default(Method.POST, URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#delete`") {
      val expected = Request(
        Body.empty,
        Headers.empty,
        Method.DELETE,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.delete(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#get`") {
      val expected = Request(
        Body.empty,
        Headers.empty,
        Method.GET,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.get(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#options`") {
      val expected = Request(
        Body.empty,
        Headers.empty,
        Method.OPTIONS,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.options(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#patch`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.PATCH,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.patch(body, URL.empty)
      assertTrue(actual == expected)
    },
    test("`#post`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.POST,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.post(body, URL.empty)
      assertTrue(actual == expected)
    },
    test("`#put`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        body,
        Headers.empty,
        Method.PUT,
        URL.empty,
        Version.`HTTP/1.1`,
        None,
      )

      val actual = Request.put(body, URL.empty)
      assertTrue(actual == expected)
    },
  )

}
