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
import zio.test.{ZIOSpecDefault, _}

import zio.http.internal.HttpGen
import zio.http.{URL => Zurl}

object LocationSpec extends ZIOSpecDefault {

  override def spec = suite("Location header suite")(
    test("Location with Empty Value") {
      assertTrue(Location.toLocation("") == Location.EmptyLocationValue) &&
      assertTrue(Location.fromLocation(Location.EmptyLocationValue) == "")
    },
    test("parsing of valid Location values") {
      check(HttpGen.request) { genRequest =>
        val toLocation    = Location.toLocation(genRequest.location.fold("")(_.toString))
        val locationValue =
          genRequest.location.fold[Location](Location.EmptyLocationValue)(x => Location.toLocation(x.toString))

        assertTrue(toLocation == locationValue)
      }

    },
  )

}
