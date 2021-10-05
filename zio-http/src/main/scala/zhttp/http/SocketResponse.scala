package zhttp.http

object SocketResponse {
  def apply[R, E](headers: List[Header] = Nil, data: HttpData[R, E] = HttpData.empty): Response[R, E] =
    Response(status = Status.SWITCHING_PROTOCOLS, headers, data)
}
