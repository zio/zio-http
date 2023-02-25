package zio.http

import scala.language.implicitConversions

package object codec {
  type ContentCodec[A] = HttpCodec[HttpCodecType.Content, A]
  type HeaderCodec[A]  = HttpCodec[HttpCodecType.Header, A]
  type MethodCodec[A]  = HttpCodec[HttpCodecType.Method, A]
  type PathCodec[A]    = HttpCodec[HttpCodecType.Path, A]
  type QueryCodec[A]   = HttpCodec[HttpCodecType.Query, A]
  type StatusCodec[A]  = HttpCodec[HttpCodecType.Status, A]
}
