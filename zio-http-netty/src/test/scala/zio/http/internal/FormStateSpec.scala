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

package zio.http.internal

import java.nio.charset.StandardCharsets

import zio._
import zio.test._

import zio.http.{Boundary, ZIOHttpSpec}

object FormStateSpec extends ZIOHttpSpec {

  val CR = '\r'

  val formExample1 = s"""|--AaB03x${CR}
                         |Content-Disposition: form-data; name="submit-name"${CR}
                         |Content-Type: text/plain${CR}
                         |${CR}
                         |Larry${CR}
                         |--AaB03x${CR}
                         |Content-Disposition: form-data; name="files"; filename="file1.txt"${CR}
                         |Content-Type: text/plain${CR}
                         |${CR}
                         |... contents of file1.txt ...${CR}
                         |--AaB03x--${CR}""".stripMargin.getBytes(StandardCharsets.UTF_8)
  def spec         = suite("FormStateSpec")(
    test("FormStateAccum") {

      val lastByte = Some('\r')

      def wasNewline(byte: Byte): Boolean = lastByte.contains('\r') && byte == '\n'

      val id       = "AaB03x"
      val start    = Chunk.fromArray(s"--$id".getBytes())
      val end      = Chunk.fromArray(s"--$id--".getBytes())
      val boundary = Boundary(id)

      assertTrue(
        wasNewline('\n'),
        boundary.isEncapsulating(start),
        boundary.isClosing(end),
      )
    },
  )

}
