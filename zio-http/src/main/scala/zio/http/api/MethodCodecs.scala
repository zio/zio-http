package zio.http.api

import zio.http.api.CodecType.Method
import zio.http.api.internal.TextCodec

private[api] trait MethodCodecs {
  def method(method: zio.http.model.Method): HttpCodec[CodecType.Method, Unit] =
    HttpCodec.Method(TextCodec.constant(method.toString()))

  def method: HttpCodec[CodecType.Method, zio.http.model.Method] =
    HttpCodec
      .Method(TextCodec.string)
      .transform(
        methodStr => zio.http.model.Method.fromString(methodStr),
        method => method.text,
      )

  def get: HttpCodec[Method, Unit]    = method(zio.http.model.Method.GET)
  def put: HttpCodec[Method, Unit]    = method(zio.http.model.Method.PUT)
  def post: HttpCodec[Method, Unit]   = method(zio.http.model.Method.POST)
  def delete: HttpCodec[Method, Unit] = method(zio.http.model.Method.DELETE)

}
