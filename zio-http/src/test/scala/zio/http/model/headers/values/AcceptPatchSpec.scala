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

import zio.Chunk
import zio.test._

import zio.http.model.MimeDB
import zio.http.model.headers.values.AcceptPatch._

object AcceptPatchSpec extends ZIOSpecDefault with MimeDB {
  override def spec = suite("AcceptPatch header suite")(
    test("AcceptPatch header transformation must be symmetrical") {
      assertTrue(
        AcceptPatch.toAcceptPatch(AcceptPatch.fromAcceptPatch(AcceptPatchValue(Chunk(text.`html`))))
          == AcceptPatchValue(Chunk(text.`html`)),
      )
    },
    test("invalid values parsing") {
      assertTrue(AcceptPatch.toAcceptPatch("invalidString") == InvalidAcceptPatchValue) &&
      assertTrue(AcceptPatch.toAcceptPatch("") == InvalidAcceptPatchValue)
    },
  )
}
