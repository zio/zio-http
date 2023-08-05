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
        method = Method.POST,
        body = body,
      )

      val actual = Request.post(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#delete`") {
      val expected = Request(
        method = Method.DELETE,
      )

      val actual = Request.delete(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#get`") {
      val expected = Request(
        method = Method.GET,
      )

      val actual = Request.get(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#options`") {
      val expected = Request(
        method = Method.OPTIONS,
      )

      val actual = Request.options(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#patch`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        method = Method.PATCH,
        body = body,
      )

      val actual = Request.patch(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#post`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        method = Method.POST,
        body = body,
      )

      val actual = Request.post(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#put`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        method = Method.PUT,
        body = body,
      )

      val actual = Request.put(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("path string") {
      val expected = Request(method = Method.GET, url = url"/foo/bar")
      val actual   = Request.get("/foo/bar")
      assertTrue(actual == expected)
    },
    test("absolute url string") {
      val expected = Request(method = Method.GET, url = url"https://foo.com/bar")
      val actual   = Request.get("https://foo.com/bar")
      assertTrue(actual == expected)
    },
  )

}
