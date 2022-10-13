package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
private[api] trait QueryInputs {

  def query(name: String): HttpCodec[CodecType.Query, String] =
    In.Query(name, TextCodec.string)

  def queryBool(name: String): HttpCodec[CodecType.Query, Boolean] =
    In.Query(name, TextCodec.boolean)

  def queryInt(name: String): HttpCodec[CodecType.Query, Int] =
    In.Query(name, TextCodec.int)

  def queryAs[A](name: String)(implicit codec: TextCodec[A]): HttpCodec[CodecType.Query, A] =
    In.Query(name, codec)

}
