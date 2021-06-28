package zhttp.http

import zhttp.socket.SocketApp

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response extends ResponseHelpers {
  // Constructors
  final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]
      with HasHeaders
      with HeadersHelpers
      with CookieHelpers

  final case class SocketResponse[-R, +E](socket: SocketApp[R, E] = SocketApp.empty) extends Response[R, E]
}
