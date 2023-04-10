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

import zio.http.Header.AcceptLanguage

object AcceptLanguageSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Accept Language header suite")(
      test("accept language header transformation must be symmetrical") {
        check(acceptLanguageStr) { header =>
          assertTrue(AcceptLanguage.render(AcceptLanguage.parse(header).toOption.get) == header)
        } &&
        check(acceptLanguageWithWeightStr) { header =>
          assertTrue(AcceptLanguage.render(AcceptLanguage.parse(header).toOption.get) == header)
        }
      },
      test("empty input should yield invalid header value") {
        assertTrue(AcceptLanguage.parse("").isLeft)
      },
      test("presence of invalid characters should yield invalid value") {
        assertTrue(AcceptLanguage.parse("!").isLeft)
      },
    )

  private def acceptLanguageStr: Gen[Any, String] =
    for {
      part1 <- Gen.stringN(2)(Gen.alphaChar)
      part2 <- Gen.stringN(2)(Gen.alphaChar)
    } yield s"$part1-$part2"

  private def acceptLanguageWithWeightStr: Gen[Any, String] =
    for {
      acceptLang <- acceptLanguageStr
      weight     <- Gen.double(0.0, 1.0)
    } yield s"$acceptLang;q=$weight"

}
