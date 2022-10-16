package zio.http.model.headers.values

import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}
import scala.util.Try

/**
 * The Access-Control-Max-Age response header indicates how long the results of
 * a preflight request (that is the information contained in the
 * Access-Control-Allow-Methods and Access-Control-Allow-Headers headers) can be
 * cached.
 *
 * Maximum number of seconds the results can be cached, as an unsigned
 * non-negative integer. Firefox caps this at 24 hours (86400 seconds). Chromium
 * (prior to v76) caps at 10 minutes (600 seconds). Chromium (starting in v76)
 * caps at 2 hours (7200 seconds). The default value is 5 seconds.
 */
sealed trait AccessControlMaxAge {
  val seconds: String
}

object AccessControlMaxAge {

  /**
   * Valid AccessControlMaxAge with an unsigned non-negative negative for
   * seconds
   */
  final case class ValidAccessControlMaxAge(private val duration: FiniteDuration = Duration(5, SECONDS))
      extends AccessControlMaxAge {
    override val seconds: String = duration.toSeconds.toString
  }

  def fromAccessControlMaxAge(accessControlMaxAge: AccessControlMaxAge): String = {
    accessControlMaxAge.seconds
  }

  def toAccessControlMaxAge(seconds: String): AccessControlMaxAge = {
    Try(seconds.toLong).fold(
      _ => ValidAccessControlMaxAge(),
      long => if (long > 0) ValidAccessControlMaxAge(Duration(long, SECONDS)) else ValidAccessControlMaxAge(),
    )
  }
}
