package zhttp.http

trait RouteDecoderModule {
  abstract class RouteDecode[A](f: String => Option[A]) {
    def unapply(a: String): Option[A] = f(a)
  }

  object boolean extends RouteDecode(_.toBooleanOption)
  object byte    extends RouteDecode(_.toByteOption)
  object short   extends RouteDecode(_.toShortOption)
  object int     extends RouteDecode(_.toIntOption)
  object long    extends RouteDecode(_.toLongOption)
  object float   extends RouteDecode(_.toFloatOption)
  object double  extends RouteDecode(_.toDoubleOption)
}
