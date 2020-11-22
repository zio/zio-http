package zio.web.codec

import zio.schema._

import zio.stream.ZTransducer

trait Codec {
  def encoder[A](schema: Schema[A]): ZTransducer[Any, Nothing, A, Byte]
  def decoder[A](schema: Schema[A]): ZTransducer[Any, String, Byte, A]
}
