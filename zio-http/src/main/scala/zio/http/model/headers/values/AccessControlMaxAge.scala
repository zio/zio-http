package zio.http.model.headers.values

import scala.util.Try

import java.time.Duration

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
  val seconds: Duration
}

object AccessControlMaxAge {

  /**
   * Valid AccessControlMaxAge with an unsigned non-negative negative for
   * seconds
   */
  final case class ValidAccessControlMaxAge(private val duration: Duration = Duration.ofSeconds(5))
      extends AccessControlMaxAge {
    override val seconds: Duration = duration
  }

  case object InvalidAccessControlMaxAge extends AccessControlMaxAge {
    override val seconds: Duration = Duration.ofSeconds(5)
  }

  def fromAccessControlMaxAge(accessControlMaxAge: AccessControlMaxAge): String = {
    accessControlMaxAge.seconds.getSeconds().toString
  }

  def toAccessControlMaxAge(seconds: String): AccessControlMaxAge = {
    Try(seconds.toLong).fold(
      _ => InvalidAccessControlMaxAge,
      long => if (long > -1) ValidAccessControlMaxAge(Duration.ofSeconds(long)) else InvalidAccessControlMaxAge,
    )
  }
}
