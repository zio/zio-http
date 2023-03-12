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

import zio.test._

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Origin.{InvalidOriginValue, OriginNull, OriginValue}
import zio.http.{Path, QueryParams}

object OriginSpec extends ZIOSpecDefault {
  override def spec = suite("Origin header suite")(
    test("Origin: null") {
      assertTrue(Origin.toOrigin("null") == OriginNull) &&
      assertTrue(Origin.fromOrigin(OriginNull) == "null")
    },
    test("parsing of invalid Origin values") {
      assertTrue(Origin.toOrigin("") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("://host") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("http://:") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("http://:80") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("host:80") == InvalidOriginValue)
    },
    test("parsing of valid without a port ") {
      assertTrue(Origin.toOrigin("http://domain") == OriginValue("http", "domain", None)) &&
      assertTrue(Origin.toOrigin("https://domain") == OriginValue("https", "domain", None))
    },
    test("parsing of valid Origin values") {
      check(HttpGen.genAbsoluteURL) { url =>
        val justSchemeHostAndPort = url.copy(path = Path.empty, queryParams = QueryParams.empty, fragment = None)
        assertTrue(
          Origin.toOrigin(justSchemeHostAndPort.encode) == OriginValue(
            url.scheme.map(_.encode).getOrElse(""),
            url.host.getOrElse(""),
            url.portIfNotDefault,
          ),
        )
      }
    },
    test("parsing and encoding is symmetrical") {
      check(HttpGen.genAbsoluteURL) { url =>
        val justSchemeHostAndPort = url.copy(path = Path.empty, queryParams = QueryParams.empty, fragment = None)
        assertTrue(Origin.fromOrigin(Origin.toOrigin(justSchemeHostAndPort.encode)) == justSchemeHostAndPort.encode)
      }
    },
  )
}
