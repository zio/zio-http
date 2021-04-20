package zhttp.http

import zhttp.socket.{SocketChannel, SocketDecoder, SocketProtocol}

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response extends ResponseOps {
  // Constructors
  final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]

  final case class SocketResponse[-R, +E](
    socket: SocketChannel[R, E] = SocketChannel.empty,
    protocol: SocketProtocol = SocketProtocol.default,
    decoder: SocketDecoder = SocketDecoder.default,
  ) extends Response[R, E]
}
