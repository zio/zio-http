//package zio.http.model.headers.values
//
//sealed trait SecWebSocketAccept
//
///**
// * The Sec-WebSocket-Accept header is used in the websocket opening handshake.
// * It would appear in the response headers. That is, this is header is sent from
// * server to client to inform that server is willing to initiate a websocket
// * connection.
// *
// * See:
// * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Accept
// */
//object SecWebSocketAccept {
//  final case class HashedKey(value: String) extends SecWebSocketAccept
//  case object InvalidHashedKey              extends SecWebSocketAccept
//
//  def toSecWebSocketAccept(value: String): SecWebSocketAccept = {
//    if (value.trim.isEmpty) InvalidHashedKey
//    else HashedKey(value)
//  }
//
//  def fromSecWebSocketAccept(secWebSocketAccept: SecWebSocketAccept): String = secWebSocketAccept match {
//    case HashedKey(value) => value
//    case InvalidHashedKey => ""
//  }
//}
