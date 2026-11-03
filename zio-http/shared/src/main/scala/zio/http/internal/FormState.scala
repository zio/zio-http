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

    private val tree0: ChunkBuilder[FormAST] = ChunkBuilder.make[FormAST]()
    private val buffer: ChunkBuilder[Byte]   = new ChunkBuilder.Byte
    private var bufferSize: Int              = 0
    private val closingBoundaryBytesSize     = boundary.closingBoundaryBytes.size
    private var boundaryMatches: Boolean     = true

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

      val posInLine        = bufferSize + (if (this.lastByte.isEmpty) 0 else 1)
      boundaryMatches &&= posInLine < closingBoundaryBytesSize && boundary.closingBoundaryBytes(posInLine) == byte
      val boundaryDetected = boundaryMatches && posInLine == closingBoundaryBytesSize - 1

      def flush(ast: FormAST): Unit = {
        buffer.clear()
        bufferSize = 0
        boundaryMatches = true
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
        if (boundaryDetected) {
          buffer += '-'
          buffer += '-'
        }
        val ast = FormAST.makePart2(buffer.result(), boundary)

        ast match {
          case content: Content         =>
            flush(content)
            if (!dropContents) addToTree(EoL) // preserving EoL for multiline content
            self
          case EncapsulatingBoundary(_) => BoundaryEncapsulated(tree)
          case ClosingBoundary(_)       => BoundaryClosed(tree)
        }
      } else {
        if (!lastByte.isEmpty) {
          if (isBufferEmpty) isBufferEmpty = false
          // While contents are dropped, only the first bytes of a line are needed to tell a boundary delimiter
          // from content: a line longer than the closing boundary can never be one. Not buffering past that point
          // keeps memory bounded for arbitrarily large (e.g. newline-free) binary parts (#4283).
          if (!dropContents || bufferSize <= closingBoundaryBytesSize) {
            buffer += lastByte.get
            bufferSize += 1
          }
        }
        lastByte = OptionalByte.Some(byte)
        self
      }

    }

    /**
     * Stops recording the content of the current part in the tree. Boundaries
     * and headers are still tracked, but content lines are neither kept in the
     * tree nor buffered beyond what boundary detection needs. Use this when the
     * content is consumed elsewhere (e.g. streamed to the user) so that the
     * memory used by the state machine stays bounded regardless of the part's
     * size.
     */
    def startIgnoringContents: FormStateBuffer = {
      if (!dropContents) dropContents = true
      self
    }

    /** Number of bytes currently buffered for the line being parsed. */
    private[http] def bufferedBytes: Int = bufferSize

    def reset(): Unit = {
      tree0.clear()
      buffer.clear()
      bufferSize = 0
      boundaryMatches = true
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
