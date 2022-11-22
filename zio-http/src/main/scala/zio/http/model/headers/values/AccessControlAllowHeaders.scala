package zio.http.model.headers.values

import zio.Chunk

sealed trait AccessControlAllowHeaders

/**
 * The Access-Control-Allow-Headers response header is used in response to a
 * preflight request which includes the Access-Control-Request-Headers to
 * indicate which HTTP headers can be used during the actual request.
 */
object AccessControlAllowHeaders {

  final case class AccessControlAllowHeadersValue(values: Chunk[CharSequence]) extends AccessControlAllowHeaders

  case object All extends AccessControlAllowHeaders

  case object NoHeaders extends AccessControlAllowHeaders

  def fromAccessControlAllowHeaders(accessControlAllowHeaders: AccessControlAllowHeaders): String =
    accessControlAllowHeaders match {
      case AccessControlAllowHeadersValue(value) => value.mkString(", ")
      case All                                   => "*"
      case NoHeaders                             => ""
    }

  def toAccessControlAllowHeaders(value: String): AccessControlAllowHeaders = {
    value match {
      case ""          => NoHeaders
      case "*"         => All
      case headerNames =>
        AccessControlAllowHeadersValue(
          Chunk.fromArray(
            headerNames
              .split(",")
              .map(_.trim),
          ),
        )
    }
  }

}
