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
  private val CRLF = s"$CR\n"

  private val partHeaders =
    s"""Content-Disposition: form-data; name="file"; filename="file.bin"$CRLF""" +
      s"Content-Type: application/octet-stream$CRLF" +
      CRLF

  /**
   * Feeds `bytes` to `state` one by one, returning the last state. Stops early
   * if a boundary state is reached.
   */
  private def feed(state: FormState, bytes: Array[Byte]): FormState = {
    var current = state
    var i       = 0
    while (i < bytes.length) {
      current match {
        case buffer: FormState.FormStateBuffer =>
          current = buffer.append(bytes(i))
          i += 1
        case _                                 =>
          i = bytes.length
      }
    }
    current
  }

  private def bytesOf(s: String): Array[Byte] = s.getBytes(StandardCharsets.UTF_8)

  private def contentNodes(tree: Chunk[FormAST]): Int = tree.count {
    case FormAST.Content(_) | FormAST.EoL => true
    case _                                => false
  }

  def spec = suite("FormStateSpec")(
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
    suite("ignoring contents (#4283)")(
      test("content lines are recorded in the tree by default") {
        val boundary = Boundary("AaB03x")
        val state    = new FormState.FormStateBuffer(boundary)
        val headers  = feed(state, bytesOf(partHeaders))
        val before   = state.tree
        val after    = feed(state, bytesOf(s"line 1${CRLF}line 2${CRLF}line 3$CRLF"))
        assertTrue(
          headers eq state,
          after eq state,
          state.phase == FormState.Phase.Part2,
          // three content lines, each followed by an EoL node
          contentNodes(state.tree) == contentNodes(before) + 6,
        )
      },
      test("content lines are not recorded in the tree once contents are ignored") {
        val boundary = Boundary("AaB03x")
        val state    = new FormState.FormStateBuffer(boundary)
        feed(state, bytesOf(partHeaders))
        val before   = state.tree
        state.startIgnoringContents
        val lines    = Array.fill(10000)(s"payload line$CRLF").mkString
        val after    = feed(state, bytesOf(lines))
        val closed   = feed(state, bytesOf(s"--AaB03x--"))
        assertTrue(
          after eq state,
          state.tree == before,
          closed == FormState.BoundaryClosed(before),
        )
      },
      test("a content line without line breaks is not buffered once contents are ignored") {
        val boundary     = Boundary("AaB03x")
        val state        = new FormState.FormStateBuffer(boundary)
        feed(state, bytesOf(partHeaders))
        val before       = state.tree
        state.startIgnoringContents
        val after        = feed(state, Array.fill[Byte](1024 * 1024)('x'))
        val buffered     = state.bufferedBytes
        // a line that starts like the closing boundary but is longer is still content
        val lookalike    = feed(state, bytesOf(s"$CRLF--AaB03xyz$CRLF"))
        val encapsulated = feed(state, bytesOf(s"--AaB03x$CRLF"))
        assertTrue(
          after eq state,
          buffered <= boundary.closingBoundaryBytes.size + 1,
          lookalike eq state,
          state.tree == before,
          encapsulated == FormState.BoundaryEncapsulated(before),
        )
      },
      test("reset re-enables recording of contents") {
        val boundary = Boundary("AaB03x")
        val state    = new FormState.FormStateBuffer(boundary)
        feed(state, bytesOf(partHeaders))
        state.startIgnoringContents
        feed(state, bytesOf(s"dropped$CRLF"))
        state.reset()
        feed(state, bytesOf(partHeaders))
        val before   = state.tree
        feed(state, bytesOf(s"kept$CRLF"))
        assertTrue(contentNodes(state.tree) == contentNodes(before) + 2)
      },
    ),
  )

}
