package zio.http.endpoint.grpc

import scala.collection.immutable.ListMap

import zio.schema.Schema
import zio.schema.codec.ProtobufCodec

import zio.http.MediaType
import zio.http.codec.{BinaryCodecWithSchema, HttpContentCodec}

object GRPC {

  implicit def fromSchema[A](implicit schema: Schema[A]): HttpContentCodec[A] =
    HttpContentCodec.default(
      ListMap(
        MediaType.parseCustomMediaType("application/grpc").get ->
          BinaryCodecWithSchema(ProtobufCodec.protobufCodec[A], schema),
      ),
    )

}
