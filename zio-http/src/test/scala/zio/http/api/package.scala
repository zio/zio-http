package zio.http

import scala.language.implicitConversions

package object api {
  implicit def stringToIn(s: String): HttpCodec[CodecType.Route, Unit] = In.literal(s)
}
