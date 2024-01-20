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

import zio.http.internal.FormAST

object FormHeaderSpec extends ZIOHttpSpec {

  val contentType1 = "Content-Type: text/html; charset=utf-8".getBytes()
  val contextType2 = "Content-Type: multipart/form-data; boundary=something".getBytes

  def spec = suite("HeaderSpec")(
    test("Header parsing") {

      val header = FormAST.Header.fromBytes(contentType1)

      assertTrue(
        header.get.name == "Content-Type",
        header.get.fields.get("charset").get == "utf-8",
        header.get.preposition == "text/html",
      )

    },
  )

}
