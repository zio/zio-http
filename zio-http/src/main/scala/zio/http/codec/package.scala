package zio.http

import scala.language.implicitConversions

package object codec {
  type PathCodec[A]   = HttpCodec[HttpCodecType.Path, A]
  type HeaderCodec[A] = HttpCodec[HttpCodecType.Header, A]
  type QueryCodec[A]  = HttpCodec[HttpCodecType.Query, A]
  // type BodyCodec[A]   = HttpCodec[HttpCodecType.Body, A]
  type MethodCodec[A] = HttpCodec[HttpCodecType.Method, A]
  type StatusCodec[A] = HttpCodec[HttpCodecType.Status, A]

  // implicit def string2HttpCodec(string: String): PathCodec[Unit] =
  //   HttpCodec.literal(string)
}
