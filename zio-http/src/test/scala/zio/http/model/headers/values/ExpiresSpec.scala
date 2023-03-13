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

import zio.http.model.headers.values.Expires.ValidExpires

object ExpiresSpec extends ZIOSpecDefault {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Expires header suite")(
    test("parsing of invalid expires values") {
      assertTrue(Expires.toExpires("") == Expires.InvalidExpires) &&
      assertTrue(Expires.toExpires("any string") == Expires.InvalidExpires) &&
      assertTrue(Expires.toExpires("Wed 21 Oct 2015 07:28:00") == Expires.InvalidExpires) &&
      assertTrue(Expires.toExpires("21 Oct 2015 07:28:00 GMT") == Expires.InvalidExpires)
      assertTrue(Expires.toExpires("Wed 21 Oct 2015 07:28:00 GMT") == Expires.InvalidExpires)
    },
    test("parsing of valid Expires values") {
      assertTrue(
        Expires.toExpires("Wed, 21 Oct 2015 07:28:00 GMT") == Expires.ValidExpires(
          ZonedDateTime.parse("Wed, 21 Oct 2015 07:28:00 GMT", formatter),
        ),
      )
    },
    test("parsing and encoding is symmetrical") {
      check(Gen.zonedDateTime(ZonedDateTime.now(), ZonedDateTime.now().plusDays(365))) { date =>
        val zone = ZoneId.of("Australia/Sydney")
        assertTrue(
          Expires
            .toExpires(Expires.fromExpires(ValidExpires(date.withZoneSameLocal(zone))))
            .value
            .format(formatter) == date.withZoneSameLocal(zone).format(formatter),
        )
      }
    },
  )
}
