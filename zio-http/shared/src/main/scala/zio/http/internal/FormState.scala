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

import zio._
import zio.http.Boundary
import zio.http.internal.FormAST._

private[http] sealed trait FormState {
  def reset(): Unit
}

private[http] object FormState {

  final class FormStateBuffer(boundary: Boundary) extends FormState { self =>

    private val tree0: ChunkBuilder[FormAST]    = ChunkBuilder.make[FormAST]()
    private val buffer: ChunkBuilder.Byte       = new ChunkBuilder.Byte
    private val boundaryMatches: Array[Boolean] = new Array[Boolean](boundary.closingBoundaryBytes.size)

    private var lastByte: OptionalByte = OptionalByte.None
    private var isBufferEmpty          = true
    private var dropContents           = false
    private var phase0: Phase          = Phase.Part1
    private var lastTree               = null.asInstanceOf[Chunk[FormAST]]

    private def addToTree(ast: FormAST): Unit = {
      if (lastTree ne null) lastTree = null
      tree0 += ast
    }

    def tree: Chunk[FormAST] = {
      if (lastTree eq null) lastTree = tree0.result()
      lastTree
    }

    def phase: Phase = phase0

    def append(byte: Byte): FormState = {

      val crlf = byte == '\n' && !lastByte.isEmpty && lastByte.get == '\r'

      var boundaryDetected = false;
      val addThis          = if (!this.lastByte.isEmpty && buffer.knownSize == -1) 1 else 0
      val pos              = buffer.knownSize + 1 + addThis
      if (pos <= boundary.closingBoundaryBytes.size) {
        boundaryMatches.update(pos, boundary.closingBoundaryBytes(pos) == byte)
      }

      if (pos == boundary.closingBoundaryBytes.size) {
        boundaryDetected = boundaryMatches.forall(_ == true)
      }

      def flush(ast: FormAST): Unit = {
        buffer.clear()
        lastByte = OptionalByte.None
        if (crlf && isBufferEmpty && (phase eq Phase.Part1)) phase0 = Phase.Part2
        if (ast.isContent && dropContents) () else addToTree(ast)
        isBufferEmpty = true
      }

      if (crlf && (phase eq Phase.Part1)) {
        val ast = FormAST.makePart1(buffer.result(), boundary)

        ast match {
          case content: Content         => flush(content); self
          case header: Header           => flush(header); self
          case EncapsulatingBoundary(_) => BoundaryEncapsulated(tree)
          case ClosingBoundary(_)       => BoundaryClosed(tree)
        }

      } else if ((crlf || boundaryDetected) && (phase eq Phase.Part2)) {
        val ast = FormAST.makePart2(buffer.result(), boundary)

        ast match {
          case content: Content         =>
            flush(content)
            addToTree(EoL) // preserving EoL for multiline content
            self
          case EncapsulatingBoundary(_) => BoundaryEncapsulated(tree)
          case ClosingBoundary(_)       => BoundaryClosed(tree)
        }
      } else {
        if (!lastByte.isEmpty) {
          if (isBufferEmpty) isBufferEmpty = false
          buffer += lastByte.get
        }
        lastByte = OptionalByte.Some(byte)
        self
      }

    }

    def startIgnoringContents: FormStateBuffer = {
      if (!dropContents) dropContents = true
      self
    }

    def reset(): Unit = {
      tree0.clear()
      buffer.clear()
      isBufferEmpty = true
      dropContents = false
      phase0 = Phase.Part1
      lastTree = null.asInstanceOf[Chunk[FormAST]]
      lastByte = OptionalByte.None
    }
  }

  final case class BoundaryEncapsulated(buffer: Chunk[FormAST]) extends FormState {
    def reset(): Unit = ()
  }

  final case class BoundaryClosed(buffer: Chunk[FormAST]) extends FormState {
    def reset(): Unit = ()
  }

  def fromBoundary(boundary: Boundary): FormState = {
    new FormStateBuffer(boundary)
  }

  sealed trait Phase

  object Phase {

    case object Part1 extends Phase
    case object Part2 extends Phase
  }

  // Avoids boxing of Byte values
  sealed abstract class OptionalByte {
    def get: Byte
    final def isEmpty: Boolean = this eq OptionalByte.None
  }

  private object OptionalByte {
    case object None                 extends OptionalByte {
      def get: Byte = throw new NoSuchElementException("None.get")
    }
    final case class Some(get: Byte) extends OptionalByte
  }

}
