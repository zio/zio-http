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

private[http] sealed trait FormState

private[http] object FormState {

  final case class FormStateBuffer(
    tree: Chunk[FormAST],
    phase: FormState.Phase,
    buffer: Chunk[Byte],
    lastByte: Option[Byte],
    boundary: Boundary,
    dropContents: Boolean,
  ) extends FormState { self =>

    def append(byte: Byte): FormState = {

      val crlf = lastByte.contains('\r') && byte == '\n'

      val phase0 =
        if (crlf && buffer.isEmpty && phase == Phase.Part1) Phase.Part2
        else phase

      def flush(ast: FormAST): FormStateBuffer =
        self.copy(
          tree = if (ast.isContent && dropContents) tree else tree :+ ast,
          buffer = Chunk.empty,
          lastByte = None,
          phase = phase0,
        )

      if (crlf && phase == Phase.Part1) {
        val ast = FormAST.makePart1(buffer, boundary)

        ast match {
          case content: Content         => flush(content)
          case header: Header           => flush(header)
          case EncapsulatingBoundary(_) => BoundaryEncapsulated(tree)
          case ClosingBoundary(_)       => BoundaryClosed(tree)
        }

      } else if (crlf && phase == Phase.Part2) {
        val ast = FormAST.makePart2(buffer, boundary)

        ast match {
          case content: Content         =>
            val next = flush(content)
            next.copy(tree = next.tree :+ EoL) // preserving EoL for multiline content
          case EncapsulatingBoundary(_) => BoundaryEncapsulated(tree)
          case ClosingBoundary(_)       => BoundaryClosed(tree)
        }
      } else {
        self.copy(
          buffer = lastByte.map(buffer :+ _).getOrElse(buffer),
          lastByte = Some(byte),
        )
      }

    }

    def startIgnoringContents: FormStateBuffer = self.copy(dropContents = true)
  }

  final case class BoundaryEncapsulated(buffer: Chunk[FormAST]) extends FormState

  final case class BoundaryClosed(buffer: Chunk[FormAST]) extends FormState

  def fromBoundary(boundary: Boundary, lastByte: Option[Byte] = None): FormState =
    FormStateBuffer(Chunk.empty, Phase.Part1, Chunk.empty, lastByte, boundary, dropContents = false)

  sealed trait Phase

  object Phase {

    case object Part1 extends Phase
    case object Part2 extends Phase
  }

}
