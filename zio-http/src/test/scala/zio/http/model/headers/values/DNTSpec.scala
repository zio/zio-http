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

import zio.http.model.headers.values.DNT.{
  InvalidDNTValue,
  NotSpecifiedDNTValue,
  TrackingAllowedDNTValue,
  TrackingNotAllowedDNTValue,
}

object DNTSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("DNT header suite")(
    test("parse DNT headers") {
      assertTrue(DNT.toDNT("1") == TrackingAllowedDNTValue)
      assertTrue(DNT.toDNT("0") == TrackingNotAllowedDNTValue)
      assertTrue(DNT.toDNT("null") == NotSpecifiedDNTValue)
      assertTrue(DNT.toDNT("test") == InvalidDNTValue)
    },
    test("encode DNT to String") {
      assertTrue(DNT.fromDNT(TrackingAllowedDNTValue) == "1")
      assertTrue(DNT.fromDNT(TrackingNotAllowedDNTValue) == "0")
      assertTrue(DNT.fromDNT(NotSpecifiedDNTValue) == "null")
      assertTrue(DNT.fromDNT(InvalidDNTValue) == "")
    },
    test("parsing and encoding is symmetrical") {
      assertTrue(DNT.fromDNT(DNT.toDNT("1")) == "1")
      assertTrue(DNT.fromDNT(DNT.toDNT("0")) == "0")
      assertTrue(DNT.fromDNT(DNT.toDNT("null")) == "null")
      assertTrue(DNT.fromDNT(DNT.toDNT("")) == "")
    },
  )
}
