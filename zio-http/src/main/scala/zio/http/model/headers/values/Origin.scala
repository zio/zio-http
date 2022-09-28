package zio.http.model.headers.values

import scala.util.Try

/** Origin header value. */
sealed trait Origin

object Origin {

  /** The Origin header value is privacy sensitive or is an opaque origin. */
  case object OriginNull extends Origin

  /** The Origin header value contains scheme, host and maybe port. */
  final case class OriginValue(scheme: String, host: String, port: Option[Int]) extends Origin

  /** The Origin header value is invalid. */
  case object InvalidOriginValue extends Origin

  def fromOrigin(origin: Origin): String =
    origin match {
      case OriginNull                           => "null"
      case OriginValue(scheme, host, maybePort) =>
        maybePort match {
          case Some(port) => s"$scheme://$host:$port"
          case None       => s"$scheme://$host"
        }
      case InvalidOriginValue                   => ""
    }

  def toOrigin(value: String): Origin = if (value == "null") {
    OriginNull
  } else {
    val scheme = value.substring(0, math.max(0, value.indexOf(':', 1)))

    val schemeSeparatorIndex = value.indexOf("://", 1)
    val portSeparatorIndex   = value.indexOf(':', 6) // 6 is minimum possible port separator index

    val host =
      if (
        schemeSeparatorIndex != -1 &&
        portSeparatorIndex != -1 &&
        portSeparatorIndex - (schemeSeparatorIndex + 3) >= 1
      ) {
        value.substring(schemeSeparatorIndex + 3, portSeparatorIndex)
      } else null

    val maybePort = Try(value.substring(portSeparatorIndex + 1, value.length).toInt).toOption

    if (scheme.isEmpty || host == null) InvalidOriginValue else OriginValue(scheme, host, maybePort)
  }
}
