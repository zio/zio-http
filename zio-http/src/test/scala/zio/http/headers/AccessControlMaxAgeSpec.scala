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

import java.time.Duration

import zio.Scope
import zio.test._

import zio.http.Header.AccessControlMaxAge

object AccessControlMaxAgeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Acc header suite")(
    test("parsing of invalid AccessControlMaxAge values returns default") {
      assertTrue(
        AccessControlMaxAge.parse("").isLeft,
        AccessControlMaxAge.parse("any string").isLeft,
        AccessControlMaxAge.parse("-1").isLeft,
      )
    },
    test("parsing of valid AccessControlMaxAge values") {
      check(Gen.long(0, 1000000)) { long =>
        assertTrue(
          AccessControlMaxAge.parse(long.toString).map(_.duration.getSeconds.toString) == Right(
            AccessControlMaxAge.render(
              AccessControlMaxAge(Duration.ofSeconds(long)),
            ),
          ),
        )
      }
    },
    test("parsing of negative seconds AccessControlMaxAge values returns an error") {
      check(Gen.long(-1000000, -1)) { long =>
        assertTrue(
          AccessControlMaxAge.parse(long.toString).isLeft,
        )
      }
    },
  )
}
