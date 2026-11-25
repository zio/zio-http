package zio.http.endpoint.grpc

import scala.collection.immutable.ListMap

import zio.schema.Schema
import zio.schema.codec.ProtobufCodec

import zio.http.MediaType
import zio.http.codec.{BinaryCodecWithSchema, HttpContentCodec}

/** A content codec for gRPC payloads. */
object GRPC {

  /**
   * Returns an `HttpContentCodec` for `A` under the media type
   * `application/grpc`, encoding with the protobuf codec derived from the
   * implicit `Schema[A]`.
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): HttpContentCodec[A] =
    HttpContentCodec(
      ListMap(
        MediaType.parseCustomMediaType("application/grpc").get ->
          BinaryCodecWithSchema(ProtobufCodec.protobufCodec[A], schema),
      ),
    )

}
