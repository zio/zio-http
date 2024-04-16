package zio.http.codec

import zio.schema.Schema
import zio.schema.codec.BinaryCodec

final case class BinaryCodecWithSchema[A](codec: BinaryCodec[A], schema: Schema[A])

object BinaryCodecWithSchema {
  def fromBinaryCodec[A](codec: BinaryCodec[A])(implicit schema: Schema[A]): BinaryCodecWithSchema[A] =
    BinaryCodecWithSchema(codec, schema)
}
