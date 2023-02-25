package zio.http.codec

import zio.stream.ZStream

import zio.schema.Schema

private[codec] trait ContentCodecs {
  def content[A](implicit schema: Schema[A]): ContentCodec[A] =
    HttpCodec.Content(schema)

  def contentStream[A](implicit schema: Schema[A]): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(schema)
}
