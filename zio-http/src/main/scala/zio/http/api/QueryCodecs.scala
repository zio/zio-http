package zio.http.api

import zio.http.api.internal.TextCodec
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait QueryCodecs {
  def query(name: String): QueryCodec[String] =
    HttpCodec.Query(name, TextCodec.string, optional = false)

  def queryBool(name: String): QueryCodec[Boolean] =
    HttpCodec.Query(name, TextCodec.boolean, optional = false)

  def queryInt(name: String): QueryCodec[Int] =
    HttpCodec.Query(name, TextCodec.int, optional = false)

  def queryAs[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] =
    HttpCodec.Query(name, codec, optional = false)
}
