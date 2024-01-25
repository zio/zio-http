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

import zio.test._

object MediaTypeSpec extends ZIOHttpSpec {
  import MediaType._

  override def spec = suite("MediaTypeSpec")(
    test("predefined mime type parsing") {
      assertTrue(MediaType.forContentType("application/json").contains(application.`json`))
    },
    test("with boundary") {
      // NOTE: Testing with non-lowercase values on purpose as spec requires MIME type and param keys to be case-insensitive,and param values case-sensitive
      MediaType.forContentType("Multipart/form-data; Boundary=-A-") match {
        case None     => assertNever("failed to parse media type")
        case Some(mt) =>
          assertTrue(
            mt.fullType == "multipart/form-data",
            mt.parameters.get("boundary").contains("-A-"),
          )
      }
    },
    test("custom mime type parsing") {
      assertTrue(MediaType.parseCustomMediaType("custom/mime").contains(MediaType("custom", "mime")))
    },
    test("optional parameter parsing") {
      assertTrue(
        MediaType
          .forContentType("application/json;p1=1;p2=2;p3=\"quoted\"")
          .contains(
            application.`json`.copy(parameters = Map("p1" -> "1", "p2" -> "2", "p3" -> "\"quoted\"")),
          ),
      )
    },
  )
}
