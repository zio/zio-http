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

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Origin.{Null, Value}
import zio.http.{Path, QueryParams}

object OriginSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Origin header suite")(
      test("Origin: null") {
        assertTrue(Origin.toOrigin("null") == Right(Null)) &&
        assertTrue(Origin.fromOrigin(Null) == "null")
      },
      test("parsing of invalid Origin values") {
        assertTrue(Origin.toOrigin("").isLeft) &&
        assertTrue(Origin.toOrigin("://host").isLeft) &&
        assertTrue(Origin.toOrigin("http://:").isLeft) &&
        assertTrue(Origin.toOrigin("http://:80").isLeft) &&
        assertTrue(Origin.toOrigin("host:80").isLeft)
      },
      test("parsing of valid without a port ") {
        assertTrue(Origin.toOrigin("http://domain") == Right(Value("http", "domain", None))) &&
        assertTrue(Origin.toOrigin("https://domain") == Right(Value("https", "domain", None)))
      },
      test("parsing of valid Origin values") {
        check(HttpGen.genAbsoluteURL) { url =>
          val justSchemeHostAndPort = url.copy(path = Path.empty, queryParams = QueryParams.empty, fragment = None)
          assertTrue(
            Origin.toOrigin(justSchemeHostAndPort.encode) == Right(
              Value(
                url.scheme.map(_.encode).getOrElse(""),
                url.host.getOrElse(""),
                url.portIfNotDefault,
              ),
            ),
          )
        }
      },
      test("parsing and encoding is symmetrical") {
        check(HttpGen.genAbsoluteURL) { url =>
          val justSchemeHostAndPort = url.copy(path = Path.empty, queryParams = QueryParams.empty, fragment = None)
          assertTrue(
            Origin.fromOrigin(
              Origin.toOrigin(justSchemeHostAndPort.encode).toOption.get,
            ) == justSchemeHostAndPort.encode,
          )
        }
      },
    )
}
