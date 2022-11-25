package zio.http.model.headers.values

import zio.http.URL

/**
 * Location header value.
 */
sealed trait Location

object Location {

  /**
   * The Location header contains URL of the new Resource
   */
  final case class LocationValue(url: URL) extends Location

  /**
   * The URL header value is invalid.
   */
  case object EmptyLocationValue extends Location

  def fromLocation(urlLocation: Location): String = {
    urlLocation match {
      case LocationValue(url) => url.toJavaURL.fold("")(_.toString())
      case EmptyLocationValue => ""
    }

  }

  def toLocation(value: String): Location = {
    if (value == "") EmptyLocationValue
    else URL.fromString(value).fold(_ => EmptyLocationValue, url => LocationValue(url))
  }
}
