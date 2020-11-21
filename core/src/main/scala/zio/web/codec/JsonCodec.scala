package zio.web.codec

import zio.schema._
import zio.stream.ZTransducer

object JsonCodec extends Codec {
  // TODO: Implement for ZIO JSON
  override def encoder[A](codec: Schema[A]): ZTransducer[Any, Nothing, A, Byte] = ???
  override def decoder[A](codec: Schema[A]): ZTransducer[Any, String, Byte, A]  = ???
}
