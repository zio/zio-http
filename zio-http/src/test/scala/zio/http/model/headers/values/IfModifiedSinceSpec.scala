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

import java.time.{ZoneOffset, ZonedDateTime}

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object IfModifiedSinceSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("IfModifiedSinceSpec")(
    test("IfModifiedSince should be parsed correctly") {
      val ifModifiedSince = IfModifiedSince.parse("Sun, 06 Nov 1994 08:49:37 GMT")
      assertTrue(
        ifModifiedSince == Right(IfModifiedSince(ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneOffset.UTC))),
      )
    },
    test("IfModifiedSince should be parsed correctly with invalid value") {
      val ifModifiedSince = IfModifiedSince.parse("Sun, 06 Nov 1994 08:49:37")
      assertTrue(ifModifiedSince.isLeft)
    },
    test("IfModifiedSince should render correctly a valid date") {
      assertTrue(
        IfModifiedSince.render(
          IfModifiedSince(ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneOffset.UTC)),
        ) == "Sun, 6 Nov 1994 08:49:37 GMT",
      )
    },
  )

}
