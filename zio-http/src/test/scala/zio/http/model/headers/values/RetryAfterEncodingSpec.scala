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
import java.time.{Duration, ZoneId, ZonedDateTime}

import zio.Scope
import zio.test._

object RetryAfterEncodingSpec extends ZIOSpecDefault {
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Retry-After header encoder suite")(
    test("parsing invalid retry after values") {
      assertTrue(
        RetryAfter.parse("").isLeft,
        RetryAfter.parse("-1").isLeft,
        RetryAfter.parse("21 Oct 2015 07:28:00 GMT").isLeft,
      )
    },
    test("parsing valid Retry After values") {
      assertTrue(
        RetryAfter.parse("Wed, 21 Oct 2015 07:28:00 GMT") == Right(
          RetryAfter.ByDate(ZonedDateTime.parse("Wed, 21 Oct 2015 07:28:00 GMT", formatter)),
        ) && RetryAfter.parse("20") == Right(RetryAfter.ByDuration(Duration.ofSeconds(20))),
      )
    },
    suite("Encoding header value transformation should be symmetrical")(
      test("date format") {
        check(Gen.zonedDateTime(ZonedDateTime.now(), ZonedDateTime.now().plusDays(365))) { date =>
          val zone = ZoneId.of("Australia/Sydney")
          assertTrue(
            RetryAfter.render(
              RetryAfter.parse(date.withZoneSameLocal(zone).format(formatter)).toOption.get,
            ) == date
              .withZoneSameLocal(zone)
              .format(formatter),
          )
        }
      },
      test("seconds format") {
        check(Gen.int(10, 1000)) { seconds =>
          assertTrue(
            RetryAfter.render(RetryAfter.parse(seconds.toString).toOption.get) == seconds.toString,
          )
        }
      },
    ),
  )
}
