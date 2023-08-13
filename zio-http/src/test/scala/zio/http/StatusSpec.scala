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

package zio.http

import zio.test.Assertion._
import zio.test._

import zio.http.internal.HttpGen

object StatusSpec extends ZIOHttpSpec {
  private val statusGen = HttpGen.status

  def spec = suite("Status")(
    toAppSpec,
    toResponseSpec,
  )

  def toResponseSpec =
    suite("toResponse")(
      test("status") {
        checkAll(statusGen) { case status =>
          assert(status.toResponse.status)(equalTo(status))
        }
      },
    )

  def toAppSpec = {
    suite("toHttpApp")(
      test("status") {
        checkAll(statusGen) { case status =>
          val res = status.toHttpApp.runZIO(Request.get(URL.empty))
          assertZIO(res.map(_.status))(equalTo(status))
        }
      },
    )
  }
}
