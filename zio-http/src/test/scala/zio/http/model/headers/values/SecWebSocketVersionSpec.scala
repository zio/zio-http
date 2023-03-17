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

object SecWebSocketVersionSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketVersion suite")(
    test("SecWebSocketVersion should be properly parsed for a valid string") {
      val probe = "13"
      assertTrue(SecWebSocketVersion.parse(probe) == Right(SecWebSocketVersion(13)))
    },
    test("SecWebSocketVersion should be properly parsed for an invalid string value") {
      val probe = "22"
      assertTrue(SecWebSocketVersion.parse(probe).isLeft)
    },
    test("SecWebSocketVersion should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketVersion.parse(probe).isLeft)
    },
    test("SecWebSocketVersion should properly render a valid string") {
      val probe = "13"
      assertTrue(SecWebSocketVersion.render(SecWebSocketVersion(13)) == probe)
    },
  )
}
