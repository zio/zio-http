package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames

import java.security.MessageDigest
import java.util.Base64

object SocketResponse {
  def apply[R, E](
    headers: List[Header] = Nil,
    data: HttpData.Socket[R, E],
    req: Request,
  ): Response[R, E] = {
    Response(
      status = Status.SWITCHING_PROTOCOLS,
      headers = headers ++ webSocketHeaders(req),
      data = data,
    )
  }
  private def webSocketHeaders(req: Request) =
    List(
      Header.custom(HttpHeaderNames.UPGRADE.toString(), "websocket"),
      Header.custom(HttpHeaderNames.CONNECTION.toString(), "upgrade"),
      Header.custom(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT.toString(), secWebSocketAcceptHeader(req)),
    )

  private def secWebSocketAcceptHeader(req: Request) = {
    val secWebSocketKey: Option[String] = req.getHeaderValue("Sec-WebSocket-Key")
    val globalUID                       = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    val combinedKey                     = secWebSocketKey.getOrElse("") + globalUID
    val sha1                            = MessageDigest.getInstance("SHA-1")
    Base64.getEncoder.encodeToString(sha1.digest(combinedKey.getBytes()))
  }
}
