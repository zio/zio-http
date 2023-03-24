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
import zio.http.model.Header.ProxyAuthenticate

object ProxyAuthenticateSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("ProxyAuthenticateSpec")(
      test("parsing of invalid inputs") {
        assertTrue(
          ProxyAuthenticate.parse("invalid").isLeft,
          ProxyAuthenticate.parse("!123456 realm=somerealm").isLeft,
        )
      },
      test("parsing of valid inputs") {
        check(HttpGen.authSchemes) { scheme =>
          assertTrue(
            ProxyAuthenticate.parse(scheme.name) == Right(ProxyAuthenticate(scheme, None)),
          )
        } &&
        check(HttpGen.authSchemes, Gen.alphaNumericStringBounded(4, 6)) { (scheme, realm) =>
          assertTrue(
            ProxyAuthenticate.parse(s"${scheme.name} realm=$realm") == Right(
              ProxyAuthenticate(scheme, Some(realm)),
            ),
          )
        }
      },
      test("parsing and encoding is symmetrical") {
        check(HttpGen.authSchemes, Gen.alphaNumericStringBounded(4, 6)) { (scheme, realm) =>
          val header = s"${scheme.name} realm=$realm"
          assertTrue(
            ProxyAuthenticate.render(
              ProxyAuthenticate.parse(header).toOption.get,
            ) == header,
          )
        }
      },
    )
}
