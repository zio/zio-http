package zio.http.codec.internal

import zio.Chunk

import zio.http.codec.HttpCodec._
import zio.http.codec._

private[http] final case class AtomizedCodecs(
  method: Chunk[TextCodec[_]],
  path: Chunk[TextCodec[_]],
  query: Chunk[Query[_]],
  header: Chunk[Header[_]],
  body: Chunk[BodyCodec[_]],
  status: Chunk[TextCodec[_]],
) { self =>
  def append(atom: Atom[_, _]) = atom match {
    case path0: Path[_]         => self.copy(path = path :+ path0.textCodec)
    case method0: Method[_]     => self.copy(method = method :+ method0.methodCodec)
    case query0: Query[_]       => self.copy(query = query :+ query0)
    case header0: Header[_]     => self.copy(header = header :+ header0)
    case body0: Body[_]         => self.copy(body = body :+ BodyCodec.Single(body0.schema))
    case status0: Status[_]     => self.copy(status = status :+ status0.textCodec)
    case stream0: BodyStream[_] => self.copy(body = body :+ BodyCodec.Multiple(stream0.schema))
  }

  def makeInputsBuilder(): Mechanic.InputsBuilder = {
    Atomized(
      Array.ofDim(method.length),
      Array.ofDim(status.length),
      Array.ofDim(path.length),
      Array.ofDim(query.length),
      Array.ofDim(header.length),
      Array.ofDim(body.length),
    )
  }
}

private[http] object AtomizedCodecs {
  val empty = AtomizedCodecs(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)

  def flatten[R, A](in: HttpCodec[R, A]): AtomizedCodecs = {
    var result = AtomizedCodecs.empty
    flattenedAtoms(in).foreach { atom =>
      result = result.append(atom)
    }
    result
  }

  private def flattenedAtoms[R, A](in: HttpCodec[R, A]): Chunk[Atom[_, _]] =
    in match {
      case Combine(left, right, _)       => flattenedAtoms(left) ++ flattenedAtoms(right)
      case atom: Atom[_, _]              => Chunk(atom)
      case map: TransformOrFail[_, _, _] => flattenedAtoms(map.api)
      case WithDoc(api, _)               => flattenedAtoms(api)
      case Empty                         => Chunk.empty
      case Halt                          => Chunk.empty
      case Fallback(_, _) => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }
}
