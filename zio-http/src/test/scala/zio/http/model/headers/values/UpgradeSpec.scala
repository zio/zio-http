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

object UpgradeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Upgrade suite")()
  suite("Upgrade header value transformation should be symmetrical")(
    test("single value") {
      assertTrue(Upgrade.render(Upgrade.parse("h2c").toOption.get) == "h2c")
    },
    test("multiple values") {
      assertTrue(
        Upgrade.render(
          Upgrade.parse("HTTP/2.0, SHTTP/1.3, IRC/6.9, RTA/x11").toOption.get,
        ) == "HTTP/2.0, SHTTP/1.3, IRC/6.9, RTA/x11",
      )
    },
  )
}
