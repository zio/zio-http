package zio.http.forms

import zio._

import zio.http.forms.FormAST._

private[forms] sealed trait FormState

private[forms] object FormState {

  final case class FormStateBuffer(
    tree: Chunk[FormAST],
    phase: FormState.Phase,
    buffer: Chunk[Byte],
    lastByte: Option[Byte],
    boundary: Boundary,
    eols: Int = 0,
  ) extends FormState { self =>

    def append(byte: Byte): FormState = {

      val crlf = lastByte.contains('\r') && byte == '\n'

      val (eols0, phase0) = (eols, phase, crlf) match {
        case (_, Phase.Part2, _)    => (0, Phase.Part2)
        case (0, Phase.Part1, true) => (1, Phase.Part1)
        case (1, Phase.Part1, true) => (0, Phase.Part2)
        case _                      => (eols, phase)
      }

      def flush(ast: FormAST): FormState =
        self.copy(
          tree = tree :+ ast,
          buffer = Chunk.empty,
          lastByte = None,
          phase = phase0,
          eols = eols0,
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
          case content: Content         => flush(content)
          case EncapsulatingBoundary(_) => BoundaryEncapsulated(tree)
          case ClosingBoundary(_)       => BoundaryClosed(tree)
        }
      } else {
        self.copy(
          buffer = lastByte.map(buffer :+ _).getOrElse(buffer),
          lastByte = Some(byte),
          eols = 0,
        )
      }

    }
  }

  final case class BoundaryEncapsulated(buffer: Chunk[FormAST]) extends FormState

  final case class BoundaryClosed(buffer: Chunk[FormAST]) extends FormState

  def fromBoundary(boundary: Boundary, lastByte: Option[Byte] = None): FormState =
    FormStateBuffer(Chunk.empty, Phase.Part1, Chunk.empty, lastByte, boundary)

  sealed trait Phase

  object Phase {

    case object Part1 extends Phase
    case object Part2 extends Phase
  }

}
