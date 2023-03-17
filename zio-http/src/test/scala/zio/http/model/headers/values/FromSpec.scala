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

object FromSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("From header suite")(
      test("parse valid value") {
        assertTrue(From.parse("test.test.tes@email.com") == Right(From("test.test.tes@email.com"))) &&
        assertTrue(From.parse("test==@email.com") == Right(From("test==@email.com"))) &&
        assertTrue(From.parse("test/d@email.com") == Right(From("test/d@email.com"))) &&
        assertTrue(From.parse("test/d@email.com") == Right(From("test/d@email.com"))) &&
        assertTrue(
          From.parse("test11!#$%&'*+-/=?^_`{|}~@email.com") == Right(From("test11!#$%&'*+-/=?^_`{|}~@email.com")),
        )

      },
      test("parse invalid value") {
        assertTrue(From.parse("t").isLeft) &&
        assertTrue(From.parse("t@p").isLeft) &&
        assertTrue(From.parse("").isLeft) &&
        assertTrue(From.parse("test@email").isLeft) &&
        assertTrue(From.parse("test.com").isLeft) &&
        assertTrue(From.parse("@email.com").isLeft) &&
        assertTrue(From.parse("@com").isLeft)
      },
    )
}
