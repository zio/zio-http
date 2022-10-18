package zio.http

import scala.language.implicitConversions

package object api {
  type RouteCodec[A]  = HttpCodec[CodecType.Route, A]
  type HeaderCodec[A] = HttpCodec[CodecType.Header, A]
  type QueryCodec[A]  = HttpCodec[CodecType.Query, A]
  type BodyCodec[A]   = HttpCodec[CodecType.Body, A]

  implicit def string2HttpCodec(string: String): RouteCodec[Unit] =
    HttpCodec.literal(string)
}
