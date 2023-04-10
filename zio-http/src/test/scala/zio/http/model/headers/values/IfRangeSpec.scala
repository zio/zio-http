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

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.Header.IfRange

object IfRangeSpec extends ZIOSpecDefault {

  private val webDateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("If-Range header encoder suite")(
      test("parsing valid eTag value") {
        assertTrue(
          IfRange.parse(""""675af34563dc-tr34"""") ==
            Right(IfRange.ETag("675af34563dc-tr34")),
        )
      },
      test("parsing valid date time value") {
        assertTrue(
          IfRange.parse("Wed, 21 Oct 2015 07:28:00 GMT") ==
            Right(IfRange.DateTime(ZonedDateTime.parse("Wed, 21 Oct 2015 07:28:00 GMT", webDateTimeFormatter))),
        )
      },
      test("parsing invalid eTag value") {
        assertTrue(
          IfRange.parse("675af34563dc-tr34").isLeft,
        )
      },
      test("parsing invalid date time value") {
        assertTrue(
          IfRange.parse("21 Oct 2015 07:28:00").isLeft,
        )
      },
      test("parsing empty value") {
        assertTrue(
          IfRange.parse("").isLeft,
        )
      },
      test("transformations are symmetrical") {
        assertTrue(
          IfRange.render(IfRange.parse(""""975af34563dc-tr34"""").toOption.get) == """"975af34563dc-tr34"""",
        ) &&
        assertTrue(
          IfRange.render(
            IfRange.parse("Fri, 28 Oct 2022 01:01:01 GMT").toOption.get,
          ) == "Fri, 28 Oct 2022 01:01:01 GMT",
        )
      },
    )
}
