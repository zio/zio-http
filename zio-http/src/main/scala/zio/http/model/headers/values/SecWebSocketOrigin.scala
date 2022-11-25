package zio.http.model.headers.values

import zio.http.URL

sealed trait SecWebSocketOrigin

/**
 * The Sec-WebSocket-Origin header is used to protect against unauthorized
 * cross-origin use of a WebSocket server by scripts using the |WebSocket| API
 * in a Web browser. The server is informed of the script origin generating the
 * WebSocket connection request.
 */
object SecWebSocketOrigin {
  final case class OriginValue(url: URL) extends SecWebSocketOrigin

  case object EmptyOrigin extends SecWebSocketOrigin

  def fromSecWebSocketOrigin(urlLocation: SecWebSocketOrigin): String = {
    urlLocation match {
      case OriginValue(url) => url.encode
      case EmptyOrigin      => ""
    }

  }

  def toSecWebSocketOrigin(value: String): SecWebSocketOrigin = {
    if (value.trim == "") EmptyOrigin
    else URL.fromString(value).fold(_ => EmptyOrigin, url => OriginValue(url))
  }
}
