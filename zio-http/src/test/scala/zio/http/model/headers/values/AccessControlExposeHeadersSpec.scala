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

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, NonEmptyChunk, Scope}

import zio.http.model.Header.AccessControlExposeHeaders

object AccessControlExposeHeadersSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlExposeHeaders suite")(
    test("AccessControlExposeHeaders should be parsed correctly") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.Some(
        NonEmptyChunk(
          "X-Header1",
          "X-Header2",
          "X-Header3",
        ),
      )
      val accessControlExposeHeadersString = "X-Header1, X-Header2, X-Header3"
      assertTrue(
        AccessControlExposeHeaders
          .parse(accessControlExposeHeadersString) == Right(accessControlExposeHeaders),
      )
    },
    test("AccessControlExposeHeaders should be parsed correctly when * is used") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.All
      val accessControlExposeHeadersString = "*"
      assertTrue(
        AccessControlExposeHeaders
          .parse(accessControlExposeHeadersString) == Right(accessControlExposeHeaders),
      )
    },
    test("AccessControlExposeHeaders should be parsed correctly when empty string is used") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.None
      val accessControlExposeHeadersString = ""
      assertTrue(
        AccessControlExposeHeaders
          .parse(accessControlExposeHeadersString) == Right(accessControlExposeHeaders),
      )
    },
    test("AccessControlExposeHeaders should properly render NoHeadersAllowed value") {
      assertTrue(
        AccessControlExposeHeaders.render(AccessControlExposeHeaders.None) == "",
      )
    },
    test("AccessControlExposeHeaders should properly render AllowAllHeaders value") {
      assertTrue(
        AccessControlExposeHeaders.render(AccessControlExposeHeaders.All) == "*",
      )
    },
    test("AccessControlExposeHeaders should properly render AllowHeaders value") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.Some(
        NonEmptyChunk(
          "X-Header1",
          "X-Header2",
          "X-Header3",
        ),
      )
      val accessControlExposeHeadersString = "X-Header1, X-Header2, X-Header3"
      assertTrue(
        AccessControlExposeHeaders.render(
          accessControlExposeHeaders,
        ) == accessControlExposeHeadersString,
      )
    },
  )
}
