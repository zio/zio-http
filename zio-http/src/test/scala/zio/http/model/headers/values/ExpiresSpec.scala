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
import java.time.{ZoneId, ZonedDateTime}

import zio.Scope
import zio.test._

import zio.http.Header.Expires

object ExpiresSpec extends ZIOSpecDefault {
  private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Expires header suite")(
    test("parsing of invalid expires values") {
      assertTrue(
        Expires.parse("").isLeft,
        Expires.parse("any string").isLeft,
        Expires.parse("Wed 21 Oct 2015 07:28:00").isLeft,
        Expires.parse("21 Oct 2015 07:28:00 GMT").isLeft,
        Expires.parse("Wed 21 Oct 2015 07:28:00 GMT").isLeft,
      )
    },
    test("parsing of valid Expires values") {
      assertTrue(
        Expires.parse("Wed, 21 Oct 2015 07:28:00 GMT") == Right(
          Expires(
            ZonedDateTime.parse("Wed, 21 Oct 2015 07:28:00 GMT", formatter),
          ),
        ),
      )
    },
    test("parsing and encoding is symmetrical") {
      check(Gen.zonedDateTime(ZonedDateTime.now(), ZonedDateTime.now().plusDays(365))) { date =>
        val zone = ZoneId.of("Australia/Sydney")
        assertTrue(
          Expires
            .parse(Expires.render(Expires(date.withZoneSameLocal(zone))))
            .toOption
            .get
            .value
            .format(formatter) == date.withZoneSameLocal(zone).format(formatter),
        )
      }
    },
  )
}
