package zhttp.socket

import zhttp.socket.Socket.{Channel, Decoder, Protocol}

trait Conversion {
  import scala.language.implicitConversions

  implicit def channel[R, E](config: SocketApp[R, E]): Socket[R, E]     = Channel(config)
  implicit def protocol(protocol: SocketProtocol): Socket[Any, Nothing] = Protocol(protocol)
  implicit def decoder(decoder: SocketDecoder): Socket[Any, Nothing]    = Decoder(decoder)
}
