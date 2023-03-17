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

import zio.Scope
import zio.test._

object TrailerSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Trailer header suite")(
      test("parse valid value") {
        assertTrue(Trailer.parse("Trailer") == Right(Trailer("trailer"))) &&
        assertTrue(Trailer.parse("Max-Forwards") == Right(Trailer("max-forwards"))) &&
        assertTrue(Trailer.parse("Cache-Control") == Right(Trailer("cache-control"))) &&
        assertTrue(Trailer.parse("Content-Type") == Right(Trailer("content-type")))
      },
      test("parse invalid value") {
        assertTrue(Trailer.parse(" ").isLeft) &&
        assertTrue(Trailer.parse("Some Value").isLeft) &&
        assertTrue(Trailer.parse("Cache-Control ").isLeft)
      },
    )
}
