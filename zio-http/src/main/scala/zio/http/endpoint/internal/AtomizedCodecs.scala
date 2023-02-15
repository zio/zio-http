package zio.http.endpoint.internal

import zio.Chunk
import zio.http.codec.TextCodec
import zio.http.endpoint.HttpCodec._

private[endpoint] final case class AtomizedCodecs(
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

private[endpoint] object AtomizedCodecs {
  val empty = AtomizedCodecs(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)
}
