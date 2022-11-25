package zio.http.model.headers.values

import zio.Chunk

/**
 * The Access-Control-Expose-Headers response header allows a server to indicate
 * which response headers should be made available to scripts running in the
 * browser, in response to a cross-origin request.
 */
sealed trait AccessControlExposeHeaders

object AccessControlExposeHeaders {

  final case class AccessControlExposeHeadersValue(values: Chunk[CharSequence]) extends AccessControlExposeHeaders

  case object All extends AccessControlExposeHeaders

  case object NoHeaders extends AccessControlExposeHeaders

  def fromAccessControlExposeHeaders(accessControlExposeHeaders: AccessControlExposeHeaders): String =
    accessControlExposeHeaders match {
      case AccessControlExposeHeadersValue(value) => value.mkString(", ")
      case All                                    => "*"
      case NoHeaders                              => ""
    }

  def toAccessControlExposeHeaders(value: String): AccessControlExposeHeaders = {
    value match {
      case ""          => NoHeaders
      case "*"         => All
      case headerNames =>
        AccessControlExposeHeadersValue(
          Chunk.fromArray(
            headerNames
              .split(",")
              .map(_.trim),
          ),
        )
    }
  }

}
