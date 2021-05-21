package zhttp.http

import zhttp.socket.SocketApp

sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response extends ResponseHelpers {
  def apply[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = HttpResponse(status, headers, content)

  private[zhttp] final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]
      with HasHeaders
      with HeadersHelpers

  private[zhttp] final case class SocketResponse[-R, +E](socket: SocketApp[R, E] = SocketApp.empty)
      extends Response[R, E]
}
