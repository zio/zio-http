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
import zio.test._

import zio.http.{Header, ZIOHttpSpec}

object ForwardedSpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Forwarded suite")(
    test("parse Forwarded header") {
      val headerValue = """for="[2001:db8:cafe::17]:4711""""
      val header      = Header.Forwarded(forValues = List(""""[2001:db8:cafe::17]:4711""""))
      assertTrue(Header.Forwarded.parse(headerValue) == Right(header))
    },
    test("parse Forwarded header with multiple for values") {
      val headerValue = """for="[2001:db8:cafe::17]:4711", for=192.0.0.25"""
      val header      = Header.Forwarded(forValues = List(""""[2001:db8:cafe::17]:4711"""", "192.0.0.25"))
      assertTrue(Header.Forwarded.parse(headerValue) == Right(header))
    },
    test("parse Forwarded header with by") {
      val headerValue = """for="[2001:db8:cafe::17]:4711";by=_value"""
      val header      = Header.Forwarded(forValues = List(""""[2001:db8:cafe::17]:4711""""), by = Some("_value"))
      assertTrue(Header.Forwarded.parse(headerValue) == Right(header))
    },
    test("parse Forwarded header with host") {
      val headerValue = """for="[2001:db8:cafe::17]:4711";host=example.com"""
      val header      = Header.Forwarded(forValues = List(""""[2001:db8:cafe::17]:4711""""), host = Some("example.com"))
      assertTrue(Header.Forwarded.parse(headerValue) == Right(header))
    },
    test("parse Forwarded header with proto") {
      val headerValue = """for="[2001:db8:cafe::17]:4711";proto=https"""
      val header      = Header.Forwarded(forValues = List(""""[2001:db8:cafe::17]:4711""""), proto = Some("https"))
      assertTrue(Header.Forwarded.parse(headerValue) == Right(header))
    },
    test("parse Forwarded header with all attributes") {
      val headerValue = """for="[2001:db8:cafe::17]:4711";by=_value;host=example.com;proto=https"""
      val header      = Header.Forwarded(
        forValues = List(""""[2001:db8:cafe::17]:4711""""),
        by = Some("_value"),
        host = Some("example.com"),
        proto = Some("https"),
      )
      assertTrue(Header.Forwarded.parse(headerValue) == Right(header))
    },
  )
}
