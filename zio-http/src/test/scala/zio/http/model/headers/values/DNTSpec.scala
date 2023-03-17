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

import zio.Scope
import zio.test._

import zio.http.model.headers.values.DNT.{NotSpecified, TrackingAllowed, TrackingNotAllowed}

object DNTSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("DNT header suite")(
    test("parse DNT headers") {
      assertTrue(
        DNT.parse("0") == Right(TrackingAllowed),
        DNT.parse("1") == Right(TrackingNotAllowed),
        DNT.parse("null") == Right(NotSpecified),
        DNT.parse("test").isLeft,
      )
    },
    test("encode DNT to String") {
      assertTrue(
        DNT.render(TrackingAllowed) == "0",
        DNT.render(TrackingNotAllowed) == "1",
        DNT.render(NotSpecified) == "null",
      )
    },
    test("parsing and encoding is symmetrical") {
      assertTrue(
        DNT.render(DNT.parse("1").toOption.get) == "1",
        DNT.render(DNT.parse("0").toOption.get) == "0",
        DNT.render(DNT.parse("null").toOption.get) == "null",
      )
    },
  )
}
