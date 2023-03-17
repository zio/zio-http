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

import zio.test._
import zio.{Chunk, NonEmptyChunk, Scope}

object VarySpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Vary header suite")(
      test("parse valid values") {
        assertTrue(Vary.parse("*") == Right(Vary.Star)) &&
        assertTrue(
          Vary.parse("SOMEVALUE, ANOTHERVALUE") == Right(Vary.Headers(NonEmptyChunk("somevalue", "anothervalue"))),
        ) &&
        assertTrue(Vary.parse("some,another") == Right(Vary.Headers(NonEmptyChunk("some", "another")))) &&
        assertTrue(Vary.parse("some") == Right(Vary.Headers(NonEmptyChunk("some"))))
      },
      test("parse invalid value") {
        assertTrue(Vary.parse(",").isLeft) &&
        assertTrue(Vary.parse("").isLeft) &&
        assertTrue(Vary.parse(" ").isLeft)
      },
    )
}
