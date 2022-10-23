package zio.http

package object api {
  type RouteCodec[A]  = HttpCodec[CodecType.Route, A]
  type HeaderCodec[A] = HttpCodec[CodecType.Header, A]
  type QueryCodec[A]  = HttpCodec[CodecType.Query, A]
  type BodyCodec[A]   = HttpCodec[CodecType.Body, A]
  type MethodCodec[A] = HttpCodec[CodecType.Method, A]
  type StatusCodec[A] = HttpCodec[CodecType.Status, A]
}
