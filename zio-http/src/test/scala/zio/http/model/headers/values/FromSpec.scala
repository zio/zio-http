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
        assertTrue(From.toFrom("test.test.tes@email.com") == Right(From("test.test.tes@email.com"))) &&
        assertTrue(From.toFrom("test==@email.com") == Right(From("test==@email.com"))) &&
        assertTrue(From.toFrom("test/d@email.com") == Right(From("test/d@email.com"))) &&
        assertTrue(From.toFrom("test/d@email.com") == Right(From("test/d@email.com"))) &&
        assertTrue(
          From.toFrom("test11!#$%&'*+-/=?^_`{|}~@email.com") == Right(From("test11!#$%&'*+-/=?^_`{|}~@email.com")),
        )

      },
      test("parse invalid value") {
        assertTrue(From.toFrom("t").isLeft) &&
        assertTrue(From.toFrom("t@p").isLeft) &&
        assertTrue(From.toFrom("").isLeft) &&
        assertTrue(From.toFrom("test@email").isLeft) &&
        assertTrue(From.toFrom("test.com").isLeft) &&
        assertTrue(From.toFrom("@email.com").isLeft) &&
        assertTrue(From.toFrom("@com").isLeft)
      },
    )
}
