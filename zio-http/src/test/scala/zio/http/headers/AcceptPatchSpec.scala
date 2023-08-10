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

import zio.test._
import zio.{NonEmptyChunk, Scope}

import zio.http.Header.AcceptPatch
import zio.http.{MediaType, ZIOHttpSpec}

object AcceptPatchSpec extends ZIOHttpSpec {
  import MediaType._

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AcceptPatch header suite")(
      test("AcceptPatch header transformation must be symmetrical") {
        assertTrue(
          AcceptPatch.parse(AcceptPatch.render(AcceptPatch(NonEmptyChunk(text.`html`))))
            == Right(AcceptPatch(NonEmptyChunk(text.`html`))),
        )
      },
      test("invalid values parsing") {
        assertTrue(
          AcceptPatch.parse("invalidString").isLeft,
          AcceptPatch.parse("").isLeft,
        )
      },
    )
}
