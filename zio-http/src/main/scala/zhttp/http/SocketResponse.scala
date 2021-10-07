package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.socket.SocketApp

import java.security.MessageDigest
import java.util.Base64

object SocketResponse {
  def apply[R, E](
    headers: List[Header] = Nil,
    socketApp: SocketApp[R, E],
    webSocketKey: String,
  ): Response[R, E] = {
    Response(
      status = Status.SWITCHING_PROTOCOLS,
      headers = headers ++ webSocketHeaders(webSocketKey),
      data = HttpData.fromSocket(socketApp),
    )
  }
  private def webSocketHeaders(key: String) =
    List(
      Header.custom(HttpHeaderNames.UPGRADE.toString(), "websocket"),
      Header.custom(HttpHeaderNames.CONNECTION.toString(), "upgrade"),
      Header.custom(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT.toString(), secWebSocketAcceptHeader(key)),
    )

  private def secWebSocketAcceptHeader(key: String) = {
    val globalUID   = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    val combinedKey = key + globalUID
    val sha1        = MessageDigest.getInstance("SHA-1")
    Base64.getEncoder.encodeToString(sha1.digest(combinedKey.getBytes()))
  }
}
