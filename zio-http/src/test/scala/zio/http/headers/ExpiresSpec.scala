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

package zio.http.headers

import zio.Scope
import zio.http.Header.Expires
import zio.test._

import java.time.{ZoneOffset, ZonedDateTime}
import java.time.temporal.ChronoUnit

object ExpiresSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Expires header suite")(
    test("parsing of invalid expires values") {
      assertTrue(
        Expires.parse("").isLeft,
        Expires.parse("any string").isLeft,
      )
    },
    test("parsing of valid Expires values") {
      assertTrue(
        Expires.parse("Wed, 21 Oct 2015 07:28:00 GMT") == Right(
          Expires(
            ZonedDateTime.of(2015, 10, 21, 7, 28, 0, 0, ZoneOffset.UTC),
          ),
        ),
      )
    },
    test("parsing and encoding is symmetrical") {
      check(Gen.zonedDateTime(ZonedDateTime.now(), ZonedDateTime.now().plusDays(365))) { date =>
        val truncated = date.truncatedTo(ChronoUnit.SECONDS)
        assertTrue(
          Expires
            .parse(Expires.render(Expires(truncated)))
            .toOption
            .get
            .value
            .toInstant == truncated.toInstant,
        )
      }
    },
  )
}
