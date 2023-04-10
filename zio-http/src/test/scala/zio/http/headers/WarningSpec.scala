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

import java.time.{ZoneId, ZonedDateTime}

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.Header.Warning

object WarningSpec extends ZIOSpecDefault {

  private val validWarning            = "110 anderson/1.3.37 \"Response is stale\""
  private val validWarningWithDate    = "112 - \"cache down\" \"Wed, 21 Oct 2015 07:28:00 GMT\""
  private val stubDate: ZonedDateTime = ZonedDateTime.of(2015, 10, 21, 7, 28, 0, 0, ZoneId.of("GMT"))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Warning header suite")(
      test("Rejects Invalid Warning Code") {
        val invalidCode = "1 anderson/1.3.37 \"Response is stale\""
        assertTrue(Warning.parse(invalidCode).isLeft)
      },
      test("Rejects Invalid Warning Date") {
        val invalidDate = validWarning + " " + "invalidDate"
        assertTrue(Warning.parse(invalidDate).isLeft)
      },
      test("Rejects Missing Warning Code") {
        val missingCode = "anderson/1.3.37 \"Response is stale\""
        assertTrue(Warning.parse(missingCode).isLeft)
      },
      test("Rejects Missing Warning Agent") {
        val missingAgent = "110 \"Response is stale\""
        assertTrue(Warning.parse(missingAgent).isLeft)
      },
      test("Rejects Missing Warning Agent with date") {
        val missingAgentWithDate = "112 \"cache down\" \"Wed, 21 Oct 2015 07:28:00 GMT\""
        assertTrue(Warning.parse(missingAgentWithDate).isLeft)
      },
      test("Rejects Missing Warning Description") {
        val missingDescription = "110 anderson/1.3.37 "
        assertTrue(Warning.parse(missingDescription).isLeft)
      },
      test("Accepts Valid Warning with Date") {
        assertTrue(
          Warning.parse(validWarningWithDate) == Right(Warning(112, "-", "\"cache down\"", Some(stubDate))),
        )
      },
      test("Accepts Valid Warning without Date") {
        assertTrue(Warning.parse(validWarning) == Right(Warning(110, "anderson/1.3.37", "\"Response is stale\"")))
      },
      test("parsing and encoding is symmetrical for warning with Date") {
        val encodedWarningwithDate = Warning.render(Warning.parse(validWarningWithDate).toOption.get)
        assertTrue(encodedWarningwithDate == validWarningWithDate)
      },
      test("parsing and encoding is symmetrical for warning without Date") {
        val encodedWarning = Warning.render(Warning.parse(validWarning).toOption.get)
        assertTrue(encodedWarning == validWarning)
      },
    )

}
