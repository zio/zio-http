package zhttp.http

import java.util.UUID

trait RouteDecoderModule {
  abstract class RouteDecode[A](f: String => A) {
    def unapply(a: String): Option[A] =
      try {
        Option(f(a))
      } catch {
        case _: Throwable => None
      }
  }

  object boolean extends RouteDecode(_.toBoolean)
  object byte    extends RouteDecode(_.toByte)
  object short   extends RouteDecode(_.toShort)
  object int     extends RouteDecode(_.toInt)
  object long    extends RouteDecode(_.toLong)
  object float   extends RouteDecode(_.toFloat)
  object double  extends RouteDecode(_.toDouble)
  object uuid    extends RouteDecode(str => UUID.fromString(str))
}
