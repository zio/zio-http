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

import zio.test._

import zio.http.model.headers.values.Server.ServerName

object ServerSpec extends ZIOSpecDefault {
  override def spec = suite("Server header suite")(
    test("empty server value") {
      assertTrue(Server.toServer("") == Server.EmptyServerName) &&
      assertTrue(Server.fromServer(Server.EmptyServerName) == "")
    },
    test("valid server values") {
      assertTrue(Server.toServer("   Apache/2.4.1   ") == ServerName("Apache/2.4.1"))
      assertTrue(Server.toServer("tsa_b") == ServerName("tsa_b"))
    },
    test("parsing and encoding is symmetrical") {
      assertTrue(Server.fromServer(Server.toServer("tsa_b")) == "tsa_b")
      assertTrue(Server.fromServer(Server.toServer("  ")) == "")
    },
  )
}
