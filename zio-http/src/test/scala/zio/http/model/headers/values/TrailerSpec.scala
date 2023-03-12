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

import zio.http.model.headers.values.From.InvalidFromValue
import zio.http.model.headers.values.Trailer.{InvalidTrailerValue, TrailerValue}

object TrailerSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Trailer header suite")(
      test("parse valid value") {
        assertTrue(Trailer.toTrailer("Trailer") == TrailerValue("trailer")) &&
        assertTrue(Trailer.toTrailer("Max-Forwards") == TrailerValue("max-forwards")) &&
        assertTrue(Trailer.toTrailer("Cache-Control") == TrailerValue("cache-control")) &&
        assertTrue(Trailer.toTrailer("Content-Type") == TrailerValue("content-type"))
      },
      test("parse invalid value") {
        assertTrue(Trailer.toTrailer(" ") == InvalidTrailerValue) &&
        assertTrue(Trailer.toTrailer("Some Value") == InvalidTrailerValue) &&
        assertTrue(Trailer.toTrailer("Cache-Control ") == InvalidTrailerValue)
      },
    )
}
