package zio.http.model.headers.values

sealed trait SecWebSocketVersion

/**
 * The Sec-WebSocket-Version header field is used in the WebSocket opening
 * handshake. It is sent from the client to the server to indicate the protocol
 * version of the connection.
 *
 * See:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Version
 */
object SecWebSocketVersion {
  // https://www.iana.org/assignments/websocket/websocket.xml#version-number

  final case class Version(version: Int) extends SecWebSocketVersion
  case object InvalidVersion             extends SecWebSocketVersion

  def toSecWebSocketVersion(version: String): SecWebSocketVersion =
    try {
      val v = version.toInt
      if (v >= 0 && v <= 13) Version(v)
      else InvalidVersion
    } catch {
      case _: Throwable => InvalidVersion
    }

  def fromSecWebSocketVersion(version: SecWebSocketVersion): String = version match {
    case Version(version) => version.toString
    case InvalidVersion   => ""
  }

}
