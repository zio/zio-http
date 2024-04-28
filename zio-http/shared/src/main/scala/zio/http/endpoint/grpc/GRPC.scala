package zio.http.endpoint.grpc

import scala.collection.immutable.ListMap

import zio.schema.Schema
import zio.schema.codec.ProtobufCodec

import zio.http.MediaType
import zio.http.codec.HttpContentCodec

object GRPC {

  implicit def fromSchema[A](implicit schema: Schema[A]): HttpContentCodec[A] =
    HttpContentCodec(
      ListMap(
        MediaType.parseCustomMediaType("application/grpc").get ->
          ProtobufCodec.protobufCodec[A],
      ),
      schema,
    )

}
