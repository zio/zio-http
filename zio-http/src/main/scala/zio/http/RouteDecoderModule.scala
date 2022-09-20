package zio.http

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Instead of using just `String` as path params, using the RouteDecoderModule
 * we can extract and converted params into a specific type also.
 *
 * ```scala
 * Http.collect[Request] {
 *   case GET -> !! / "user" / int(id) => Response.text("User id requested: \${id}")
 *   case GET -> !! / "user" / name    => Response.text("User name requested: \${name}")
 * }
 * ```
 *
 * If the request looks like `GET /user/100` then it would match the first case.
 * This is because internally the `id` param can be decoded into an `Int`. If a
 * request of the form `GET /user/zio` is made, in that case the second case is
 * matched.
 */

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
  object date    extends RouteDecode(str => LocalDate.parse(str))
  object time    extends RouteDecode(str => LocalDateTime.parse(str))
}
