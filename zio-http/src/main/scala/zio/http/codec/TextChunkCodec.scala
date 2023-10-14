package zio.http.codec

import scala.annotation.tailrec

import zio.{Chunk, ChunkBuilder, NonEmptyChunk}

sealed trait TextChunkCodec[A, I] {
  def cardinality: String
  def parent: TextCodec[I]
  def decode(chunk: Chunk[String]): TextChunkCodec.DecodeResult[A]
  def encode(value: A): Chunk[String]
}

object TextChunkCodec {
  def unapply[I](codec: TextChunkCodec[_, I]): Option[TextCodec[I]] = Some(codec.parent)

  def any[I](codec: TextCodec[I]): TextChunkCodec[Chunk[I], I]               = new TextChunkCodec[Chunk[I], I] {
    def cardinality: String                                  = "any"
    def parent: TextCodec[I]                                 = codec
    def decode(chunk: Chunk[String]): DecodeResult[Chunk[I]] = _decode(codec, chunk)
    def encode(value: Chunk[I]): Chunk[String]               = value map codec.encode
  }
  def oneOrMore[I](codec: TextCodec[I]): TextChunkCodec[NonEmptyChunk[I], I] = new TextChunkCodec[NonEmptyChunk[I], I] {
    def cardinality: String                                          = "one or more"
    def parent: TextCodec[I]                                         = codec
    def decode(chunk: Chunk[String]): DecodeResult[NonEmptyChunk[I]] = {
      _decode(codec, chunk) flatMap (_.nonEmptyOrElse[DecodeResult[NonEmptyChunk[I]]](MissedData)(DecodeSuccess(_)))
    }

    def encode(value: NonEmptyChunk[I]): Chunk[String] = value map codec.encode
  }
  def optional[I](codec: TextCodec[I]): TextChunkCodec[Option[I], I]         = new TextChunkCodec[Option[I], I] {
    def cardinality: String                                            = "one or none"
    def parent: TextCodec[I]                                           = codec
    override def decode(chunk: Chunk[String]): DecodeResult[Option[I]] = chunk match {
      case Chunk(value) => if (codec.isDefinedAt(value)) DecodeSuccess(Some(codec(value))) else MalformedData(codec)
      case chunk if chunk.isEmpty => DecodeSuccess(None)
      case _                      => InvalidCardinality(chunk.length, cardinality)
    }
    override def encode(value: Option[I]): Chunk[String]               = value match {
      case Some(item) => Chunk(codec.encode(item))
      case None       => Chunk.empty
    }
  }
  def one[I](codec: TextCodec[I]): TextChunkCodec[I, I]                      = new TextChunkCodec[I, I] {
    def cardinality: String                                    = "exactly one"
    def parent: TextCodec[I]                                   = codec
    override def decode(chunk: Chunk[String]): DecodeResult[I] = chunk match {
      case Chunk(value)           => if (codec.isDefinedAt(value)) DecodeSuccess(codec(value)) else MalformedData(codec)
      case chunk if chunk.isEmpty => MissedData
      case _                      => InvalidCardinality(chunk.length, cardinality)
    }
    override def encode(value: I): Chunk[String]               = Chunk(codec.encode(value))
  }

  private def _decode[I](codec: TextCodec[I], chunk: Chunk[String]): DecodeResult[Chunk[I]] = {
    val decoded = ChunkBuilder.make[I](chunk.length)

    @tailrec def loop(i: Int): DecodeResult[Chunk[I]] = {
      if (i < chunk.length) {
        val value = chunk(i)
        if (codec.isDefinedAt(value)) {
          decoded += codec(value)
          loop(i + 1)
        } else MalformedData(codec)
      } else DecodeSuccess(decoded.result)
    }

    loop(0)
  }

  sealed trait DecodeResult[+A] {
    def flatMap[B](f: A => DecodeResult[B]): DecodeResult[B]
  }

  case class DecodeSuccess[A](value: A) extends DecodeResult[A] {
    def flatMap[B](f: A => DecodeResult[B]): DecodeResult[B] = f(value)
  }

  sealed trait DecodeFailure                                         extends DecodeResult[Nothing] {
    def flatMap[B](f: Nothing => DecodeResult[B]): DecodeResult[B] = this
  }
  case object MissedData                                             extends DecodeFailure
  final case class InvalidCardinality(actual: Int, expected: String) extends DecodeFailure
  final case class MalformedData(codec: TextCodec[_])                extends DecodeFailure
}
