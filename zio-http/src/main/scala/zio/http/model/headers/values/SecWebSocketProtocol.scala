package zio.http.model.headers.values

import zio.Chunk

sealed trait SecWebSocketProtocol

/**
 * The Sec-WebSocket-Protocol header field is used in the WebSocket opening
 * handshake. It is sent from the client to the server and back from the server
 * to the client to confirm the subprotocol of the connection. This enables
 * scripts to both select a subprotocol and be sure that the server agreed to
 * serve that subprotocol.
 *
 * See:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Protocol
 */
object SecWebSocketProtocol {
  // https://www.iana.org/assignments/websocket/websocket.xml#subprotocol-name

  final case class Protocols(subProtocols: Chunk[String]) extends SecWebSocketProtocol
  case object InvalidProtocol                             extends SecWebSocketProtocol

  def toSecWebSocketProtocol(subProtocols: String): SecWebSocketProtocol =
    if (subProtocols.trim.isEmpty) InvalidProtocol
    else Protocols(Chunk.from(subProtocols.split(",").map(_.trim)))

  def fromSecWebSocketProtocol(subProtocols: SecWebSocketProtocol): String =
    subProtocols match {
      case Protocols(subProtocols) => subProtocols.mkString(", ")
      case InvalidProtocol         => ""
    }
}
