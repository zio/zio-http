package zio.http.model.headers.values

import zio.http.URL

sealed trait SecWebSocketLocation

object SecWebSocketLocation {
  final case class LocationValue(url: URL) extends SecWebSocketLocation

  case object EmptyLocationValue extends SecWebSocketLocation

  def fromSecWebSocketLocation(urlLocation: SecWebSocketLocation): String = {
    urlLocation match {
      case LocationValue(url) => url.encode
      case EmptyLocationValue => ""
    }

  }

  def toSecWebSocketLocation(value: String): SecWebSocketLocation = {
    if (value.trim == "") EmptyLocationValue
    else URL.fromString(value).fold(_ => EmptyLocationValue, url => LocationValue(url))
  }
}
