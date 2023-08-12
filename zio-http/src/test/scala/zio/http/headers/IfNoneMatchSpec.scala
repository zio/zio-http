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

import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{NonEmptyChunk, Scope}

import zio.http.Header.IfNoneMatch
import zio.http.ZIOHttpSpec

object IfNoneMatchSpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("IfNoneMatch suite")(
    test("IfMatch '*' should be parsed correctly") {
      val ifMatch = IfNoneMatch.parse("*")
      assertTrue(ifMatch == Right(IfNoneMatch.Any))
    },
    test("IfMatch '' should be a failure") {
      val ifMatch = IfNoneMatch.parse("")
      assertTrue(ifMatch.isLeft)
    },
    test("IfMatch 'etag1, etag2' should be parsed correctly") {
      val ifMatch = IfNoneMatch.parse("etag1, etag2")
      assertTrue(ifMatch == Right(IfNoneMatch.ETags(NonEmptyChunk("etag1", "etag2"))))
    },
    test("IfMatch 'etag1, etag2' should be rendered correctly") {
      val ifMatch = IfNoneMatch.ETags(NonEmptyChunk("etag1", "etag2"))
      assertTrue(IfNoneMatch.render(ifMatch) == "etag1,etag2")
    },
  )
}
