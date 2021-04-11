package zhttp.http

import zhttp.socket.Socket

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response extends ResponseOps {
  // Constructors
  final case class HttpResponse[R](status: Status, headers: List[Header], content: HttpContent[R, String])
      extends Response[R, Nothing]
  final case class SocketResponse[-R, +E](socket: Socket[R, E]) extends Response[R, E]
}
