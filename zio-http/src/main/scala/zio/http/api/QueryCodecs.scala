package zio.http.api

import zio.http.api.internal.TextCodec

private[api] trait QueryCodecs {
  def query(name: String): QueryCodec[String] =
    HttpCodec.Query(name, TextCodec.string)

  def queryBool(name: String): QueryCodec[Boolean] =
    HttpCodec.Query(name, TextCodec.boolean)

  def queryInt(name: String): QueryCodec[Int] =
    HttpCodec.Query(name, TextCodec.int)

  def queryAs[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] =
    HttpCodec.Query(name, codec)
}
