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

object IfMatchSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("IfMatch suite")(
    test("IfMatch '*' should be parsed correctly") {
      val ifMatch = IfMatch.parse("*")
      assertTrue(ifMatch == Right(IfMatch.Any))
    },
    test("IfMatch '' should be a failure") {
      val ifMatch = IfMatch.parse("")
      assertTrue(ifMatch.isLeft)
    },
    test("IfMatch 'etag1, etag2' should be parsed correctly") {
      val ifMatch = IfMatch.parse("etag1, etag2")
      assertTrue(ifMatch == Right(IfMatch.ETags(NonEmptyChunk("etag1", "etag2"))))
    },
    test("IfMatch 'etag1, etag2' should be rendered correctly") {
      val ifMatch = IfMatch.ETags(NonEmptyChunk("etag1", "etag2"))
      assertTrue(IfMatch.render(ifMatch) == "etag1,etag2")
    },
  )
}
