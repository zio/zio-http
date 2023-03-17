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
import zio.{Chunk, Scope}

object VarySpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Vary header suite")(
      test("parse valid values") {
        assertTrue(Vary.toVary("*") == Right(Vary.Star)) &&
        assertTrue(Vary.toVary("SOMEVALUE, ANOTHERVALUE") == Right(Vary.Headers(Chunk("somevalue", "anothervalue")))) &&
        assertTrue(Vary.toVary("some,another") == Right(Vary.Headers(Chunk("some", "another")))) &&
        assertTrue(Vary.toVary("some") == Right(Vary.Headers(Chunk("some"))))
      },
      test("parse invalid value") {
        assertTrue(Vary.toVary(",").isLeft) &&
        assertTrue(Vary.toVary("").isLeft) &&
        assertTrue(Vary.toVary(" ").isLeft)
      },
    )
}
