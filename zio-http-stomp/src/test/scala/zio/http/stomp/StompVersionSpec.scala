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

package zio.http.stomp

import zio.test._

object StompVersionSpec extends ZIOSpecDefault {

  override def spec = suite("STOMP Version")(
    suite("version negotiation")(
      test("negotiate highest mutual version") {
        val clientVersions = List(StompVersion.V1_0, StompVersion.V1_1, StompVersion.V1_2)
        val serverVersions = List(StompVersion.V1_1, StompVersion.V1_2)

        val negotiated = StompVersion.negotiate(clientVersions, serverVersions)

        assertTrue(
          negotiated.contains(StompVersion.V1_2),
        )
      },
      test("negotiate when only 1.0 is mutual") {
        val clientVersions = List(StompVersion.V1_0)
        val serverVersions = List(StompVersion.V1_0, StompVersion.V1_1, StompVersion.V1_2)

        val negotiated = StompVersion.negotiate(clientVersions, serverVersions)

        assertTrue(
          negotiated.contains(StompVersion.V1_0),
        )
      },
      test("fail negotiation when no mutual version") {
        val clientVersions = List(StompVersion.V1_0)
        val serverVersions = List(StompVersion.V1_2)

        val negotiated = StompVersion.negotiate(clientVersions, serverVersions)

        assertTrue(
          negotiated.isEmpty,
        )
      },
    ),
    suite("parse accept-version header")(
      test("parse multiple versions") {
        val versions = StompVersion.parseAcceptVersions("1.0,1.1,1.2")

        assertTrue(
          versions == List(StompVersion.V1_0, StompVersion.V1_1, StompVersion.V1_2),
        )
      },
      test("parse single version") {
        val versions = StompVersion.parseAcceptVersions("1.2")

        assertTrue(
          versions == List(StompVersion.V1_2),
        )
      },
      test("parse with spaces") {
        val versions = StompVersion.parseAcceptVersions("1.0, 1.1, 1.2")

        assertTrue(
          versions == List(StompVersion.V1_0, StompVersion.V1_1, StompVersion.V1_2),
        )
      },
      test("ignore invalid versions") {
        val versions = StompVersion.parseAcceptVersions("1.0,invalid,1.2")

        assertTrue(
          versions == List(StompVersion.V1_0, StompVersion.V1_2),
        )
      },
    ),
    suite("version features")(
      test("v1.0 features") {
        assertTrue(
          !StompVersion.V1_0.supportsHeaderEscaping,
          !StompVersion.V1_0.supportsNack,
          !StompVersion.V1_0.supportsHeartbeat,
          !StompVersion.V1_0.requiresContentLength,
        )
      },
      test("v1.1 features") {
        assertTrue(
          StompVersion.V1_1.supportsHeaderEscaping,
          StompVersion.V1_1.supportsNack,
          StompVersion.V1_1.supportsHeartbeat,
          !StompVersion.V1_1.requiresContentLength,
        )
      },
      test("v1.2 features") {
        assertTrue(
          StompVersion.V1_2.supportsHeaderEscaping,
          StompVersion.V1_2.supportsNack,
          StompVersion.V1_2.supportsHeartbeat,
          StompVersion.V1_2.requiresContentLength,
        )
      },
    ),
    suite("fromString")(
      test("parse valid version strings") {
        assertTrue(
          StompVersion.fromString("1.0").contains(StompVersion.V1_0),
          StompVersion.fromString("1.1").contains(StompVersion.V1_1),
          StompVersion.fromString("1.2").contains(StompVersion.V1_2),
        )
      },
      test("return None for invalid version") {
        assertTrue(
          StompVersion.fromString("2.0").isEmpty,
          StompVersion.fromString("invalid").isEmpty,
        )
      },
    ),
  )
}
