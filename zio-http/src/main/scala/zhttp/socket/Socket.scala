package zhttp.socket

import zhttp.http.Response.SocketResponse

sealed trait Socket[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: Socket[R1, E1]): Socket[R1, E1] = Socket.Concat(self, other)
  def asResponse: SocketResponse[R, E]                            = Socket.asResponse(self)
}

object Socket {
  private case class Concat[R, E](a: Socket[R, E], b: Socket[R, E])  extends Socket[R, E]
  private case class SocketConfig[R, E](socket: SocketChannel[R, E]) extends Socket[R, E]
  private case class Protocol(config: SocketProtocol)                extends Socket[Any, Nothing]
  private case class Decoder(config: SocketDecoder)                  extends Socket[Any, Nothing]

  def config[R, E](config: SocketChannel[R, E]): Socket[R, E]  = SocketConfig(config)
  def protocol(protocol: SocketProtocol): Socket[Any, Nothing] = Protocol(protocol)
  def decoder(decoder: SocketDecoder): Socket[Any, Nothing]    = Decoder(decoder)

  def asResponse[R, E](self: Socket[R, E]): SocketResponse[R, E] = {
    def loop(socketB: Socket[R, E], res: SocketResponse[R, E]): SocketResponse[R, E] = {
      socketB match {
        case Concat(a, b)         => loop(b, loop(a, res))
        case SocketConfig(socket) => res.copy(socket = res.socket ++ socket)
        case Protocol(config)     => res.copy(protocol = res.protocol ++ config)
        case Decoder(config)      => res.copy(decoder = res.decoder ++ config)
      }
    }
    loop(self, SocketResponse())
  }
}
