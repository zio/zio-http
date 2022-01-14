package zhttp.socket

sealed trait IsWebSocket[+R, -E, +A, -B] {
  def apply[R1 >: R, E1 <: E, A1 >: A, B1 <: B](
    socket: Socket[R1, E1, A1, B1],
  ): Socket[R1, Throwable, WebSocketFrame, WebSocketFrame] =
    socket.asInstanceOf[Socket[R1, Throwable, WebSocketFrame, WebSocketFrame]]
}

object IsWebSocket extends IsWebSocket[Nothing, Any, WebSocketFrame, WebSocketFrame] {
  implicit def webSocketFrame[R]: IsWebSocket[R, Throwable, WebSocketFrame, WebSocketFrame] = IsWebSocket
}
