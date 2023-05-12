package zio.http.codec.internal

import zio._

import zio.schema.Schema
import zio.schema.codec._

import zio.http._

sealed trait MediaTypeCodec {

  def acceptedTypes: Chunk[MediaType]
  def decoders: Chunk[Body => IO[Throwable, _]]
  def decodeSingle(body: Body): IO[Throwable, Any] = decoders(0)(body)
  def encoders: Chunk[Any => Body]
  def encodeSingle(a: Any): Body                   = encoders(0)(a)

}

object MediaTypeCodec {
  def apply(
    content: Chunk[BodyCodec[_]],
    binaryCodec: Schema[Any] => BinaryCodec[Any],
    acceptTypes: Chunk[MediaType],
  ): MediaTypeCodec = new MediaTypeCodec {
    override def encoders: Chunk[Any => Body] =
      content.map { bodyCodec =>
        val erased    = bodyCodec.erase
        val jsonCodec = binaryCodec(erased.schema.asInstanceOf[Schema[Any]]).asInstanceOf[BinaryCodec[erased.Element]]
        erased.encodeToBody(_, jsonCodec)
      }

    override def decoders: Chunk[Body => IO[Throwable, _]] =
      content.map { bodyCodec =>
        val jsonCodec =
          binaryCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
            .asInstanceOf[BinaryCodec[bodyCodec.Element]]
        bodyCodec.decodeFromBody(_, jsonCodec)
      }

    override val acceptedTypes: Chunk[MediaType] = acceptTypes
  }
  def json(content: Chunk[BodyCodec[_]]): MediaTypeCodec =
    apply(
      content,
      JsonCodec.schemaBasedBinaryCodec[Any](_),
      Chunk(
        MediaType.application.`json`,
      ),
    )

  def protobuf(content: Chunk[BodyCodec[_]]): MediaTypeCodec =
    apply(
      content,
      ProtobufCodec.protobufCodec[Any](_),
      Chunk(
        MediaType.parseCustomMediaType("application/protobuf").get,
      ),
    )
}
