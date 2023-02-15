package zio.http.endpoint

import zio.http.codec.TextCodec
import zio.http.endpoint.CodecType.Method

private[endpoint] trait MethodCodecs {
  def method(method: zio.http.model.Method): HttpCodec[CodecType.Method, Unit] =
    HttpCodec.Method(TextCodec.constant(method.toString()))

  def method: HttpCodec[CodecType.Method, zio.http.model.Method] =
    HttpCodec
      .Method(TextCodec.string)
      .transform(
        methodStr => zio.http.model.Method.fromString(methodStr),
        method => method.text,
      )

  def connect: HttpCodec[Method, Unit] = method(zio.http.model.Method.CONNECT)
  def delete: HttpCodec[Method, Unit]  = method(zio.http.model.Method.DELETE)
  def get: HttpCodec[Method, Unit]     = method(zio.http.model.Method.GET)
  def options: HttpCodec[Method, Unit] = method(zio.http.model.Method.OPTIONS)
  def post: HttpCodec[Method, Unit]    = method(zio.http.model.Method.POST)
  def put: HttpCodec[Method, Unit]     = method(zio.http.model.Method.PUT)

}
