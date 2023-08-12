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

import zio.http.Header.CacheControl
import zio.http.ZIOHttpSpec
import zio.http.internal.HttpGen

object CacheControlSpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("CacheControl suite")(
    suite("CacheControl header value transformation should be symmetrical")(
      test("single value") {
        check(HttpGen.cacheControlSingleValueWithSeconds) { value =>
          assertTrue(CacheControl.parse(CacheControl.render(value)) == Right(value))
        }
      },
      test("multiple values") {
        check(HttpGen.cacheControl) { value =>
          assertTrue(CacheControl.parse(CacheControl.render(value)) == Right(value))
        }
      },
    ),
  )
}
