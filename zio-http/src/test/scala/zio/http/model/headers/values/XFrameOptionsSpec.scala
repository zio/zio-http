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
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object XFrameOptionsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("XFrameOptions suite")(
    test("parsing of invalid XFrameOptions values") {
      assertTrue(XFrameOptions.parse("").isLeft) &&
      assertTrue(XFrameOptions.parse("any string").isLeft) &&
      assertTrue(XFrameOptions.parse("DENY ") == Right(XFrameOptions.Deny)) &&
      assertTrue(XFrameOptions.parse("SAMEORIGIN ") == Right(XFrameOptions.SameOrigin))
    },
    test("rendering of XFrameOptions values") {
      assertTrue(XFrameOptions.render(XFrameOptions.Deny) == "DENY") &&
      assertTrue(XFrameOptions.render(XFrameOptions.SameOrigin) == "SAMEORIGIN")
    },
  )
}
