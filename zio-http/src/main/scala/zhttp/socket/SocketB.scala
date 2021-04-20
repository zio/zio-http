package zhttp.socket

import zhttp.http.Response.SocketResponse

// TODO rename to Socket
sealed trait SocketB[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: SocketB[R1, E1]): SocketB[R1, E1] = SocketB.Concat(self, other)
  def asResponse: SocketResponse[R, E]                              = SocketB.asResponse(self)
}

object SocketB {
  private case class Concat[R, E](a: SocketB[R, E], b: SocketB[R, E]) extends SocketB[R, E]
  private case class SocketConfig[R, E](socket: Socket[R, E])         extends SocketB[R, E]
  private case class Protocol(config: SocketProtocol)                 extends SocketB[Any, Nothing]
  private case class Decoder(config: SocketDecoder)                   extends SocketB[Any, Nothing]

  def config[R, E](config: Socket[R, E]): SocketB[R, E]         = SocketConfig(config)
  def protocol(protocol: SocketProtocol): SocketB[Any, Nothing] = Protocol(protocol)
  def decoder(decoder: SocketDecoder): SocketB[Any, Nothing]    = Decoder(decoder)

  def asResponse[R, E](self: SocketB[R, E]): SocketResponse[R, E] = {
    def loop(socketB: SocketB[R, E], res: SocketResponse[R, E]): SocketResponse[R, E] = {
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
