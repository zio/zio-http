package zhttp.http

object SocketResponse {
  def apply[R, E](headers: List[Header] = Nil, data: HttpData.Socket[R, E]): Response[R, E] =
    Response(
      status = Status.SWITCHING_PROTOCOLS,
      headers = headers ++ List(
        Header.custom("upgrade", "websocket"),
        Header.custom("connection", "upgrade"),
//        Header.custom("Sec-WebSocket-Accept", "getKey()"),
      ),
      data = data,
    )
}
