//package zio.http.model.headers.values
//
//sealed trait SecWebSocketKey
//
///**
// * The Sec-WebSocket-Key header is used in the WebSocket handshake. It is sent
// * from the client to the server to provide part of the information used by the
// * server to prove that it received a valid WebSocket handshake. This helps
// * ensure that the server does not accept connections from non-WebSocket clients
// * (e.g. HTTP clients) that are being abused to send data to unsuspecting
// * WebSocket servers.
// *
// * See:
// * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Key
// */
//object SecWebSocketKey {
//  case class Base64EncodedKey(key: String) extends SecWebSocketKey
//  case object InvalidKey                   extends SecWebSocketKey
//
//  def toSecWebSocketKey(key: String): SecWebSocketKey = {
//    try {
//      val decodedKey = java.util.Base64.getDecoder.decode(key)
//      if (decodedKey.length == 16) Base64EncodedKey(key)
//      else InvalidKey
//    } catch {
//      case _: Throwable => InvalidKey
//    }
//
//  }
//
//  def fromSecWebSocketKey(secWebSocketKey: SecWebSocketKey): String = secWebSocketKey match {
//    case Base64EncodedKey(key) => key
//    case InvalidKey            => ""
//  }
//}
