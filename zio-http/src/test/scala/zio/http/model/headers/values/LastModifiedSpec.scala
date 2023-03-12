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

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.model.headers.values.LastModified.LastModifiedDateTime

object LastModifiedSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("LastModified spec")(
    test("LastModifiedDateTime") {
      val dateTime     = ZonedDateTime.parse("Sun, 06 Nov 1994 08:49:37 GMT", DateTimeFormatter.RFC_1123_DATE_TIME)
      val lastModified = LastModified.LastModifiedDateTime(dateTime)
      assertTrue(LastModified.fromLastModified(lastModified) == "Sun, 6 Nov 1994 08:49:37 GMT")
    },
    test("LastModified  should be parsed correctly with invalid value") {
      val lastModified = LastModified.toLastModified("Mon, 07 Nov 1994 08:49:37")
      assertTrue(lastModified == LastModified.InvalidLastModified)
    },
    test("LastModified should render correctly a valid date") {
      assertTrue(
        LastModified.fromLastModified(
          LastModifiedDateTime(ZonedDateTime.of(1994, 11, 7, 8, 49, 37, 0, ZoneOffset.UTC)),
        ) == "Mon, 7 Nov 1994 08:49:37 GMT",
      )
    },
  )
}
