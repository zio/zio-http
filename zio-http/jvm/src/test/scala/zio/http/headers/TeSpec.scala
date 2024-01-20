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

import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{NonEmptyChunk, Scope}

import zio.http.Header.Te
import zio.http.Header.Te.{Deflate, GZip, Multiple, Trailers}
import zio.http.ZIOHttpSpec

object TeSpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TE suite")(
    test("parse TE header") {
      val te = "trailers, deflate;q=0.5, gzip;q=0.2"
      assertTrue(
        Te.parse(te) ==
          Right(Multiple(NonEmptyChunk(Trailers, Deflate(Some(0.5)), GZip(Some(0.2))))),
      )
    },
    test("parse TE header - simple value with weight") {
      val te = "deflate;q=0.5"
      assertTrue(
        Te.parse(te) ==
          Right(Deflate(Some(0.5))),
      )
    },
    test("parse TE header - simple value") {
      val te = "trailers"
      assertTrue(
        Te.parse(te) ==
          Right(Trailers),
      )
    },
    test("render TE header") {
      val te = "trailers, deflate;q=0.5, gzip;q=0.2"
      assertTrue(
        Te.render(Te.parse(te).toOption.get) == te,
      )
    },
  )
}
