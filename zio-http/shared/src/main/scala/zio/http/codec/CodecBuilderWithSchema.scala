package zio.http.codec

import zio.schema.Schema
import zio.schema.codec.BinaryCodec

trait CodecBuilder {
  def build[A](schema: Schema[A]): BinaryCodec[A]
}

final case class CodecBuilderWithSchema[A](codecBuilder: CodecBuilder, schema: Schema[A]) {
  private var codec0: BinaryCodec[A] = _
  def codec: BinaryCodec[A]          = {
    if (codec0 == null) codec0 = codecBuilder.build(schema)
    codec0
  }

  def optional: CodecBuilderWithSchema[Option[A]] = copy(schema = schema.optional)
}

object CodecBuilderWithSchema {
  def fromBinaryCodec[A](codecBuilder: CodecBuilder)(implicit schema: Schema[A]): CodecBuilderWithSchema[A] =
    CodecBuilderWithSchema(codecBuilder, schema)
}
