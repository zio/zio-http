package zhttp.http

import zhttp.socket.Socket

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response extends ResponseOps {
  // Constructors
  final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]

  final case class SocketResponse[-R, +E](socket: Socket[R, E]) extends Response[R, E]
}
