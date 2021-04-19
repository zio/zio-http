package zhttp.socket

import zhttp.socket.Socket._
import zio.ZIO
import zio.stream.ZStream

case class SocketConfig[-R, +E](
  onTimeout: Option[ZIO[R, Nothing, Unit]] = None,
  onOpen: Option[Connection => ZStream[R, E, WebSocketFrame]] = None,
  onMessage: Option[WebSocketFrame => ZStream[R, E, WebSocketFrame]] = None,
  onError: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
  onClose: Option[Connection => ZIO[R, Nothing, Unit]] = None,
  protocolConfig: ProtocolConfig = ProtocolConfig.empty,
  decoderConfig: DecoderConfig = DecoderConfig.empty,
)

object SocketConfig {

  def fromSocket[R, E](socket: Socket[R, E]): SocketConfig[R, E] = {
    val iSettings: SocketConfig[Any, Nothing] = SocketConfig()

    def loop(socket: Socket[R, E], s: SocketConfig[R, E]): SocketConfig[R, E] =
      socket match {
        case OnTimeout(onTimeout) =>
          s.copy(onTimeout = s.onTimeout.fold(Option(onTimeout))(v => Option(v &> onTimeout)))
        case OnOpen(onOpen)       =>
          s.copy(onOpen = s.onOpen.fold(Option(onOpen))(v => Option((c: Connection) => v(c).merge(onOpen(c)))))
        case OnMessage(onMessage) =>
          s.copy(onMessage = s.onMessage.fold(Option(onMessage))(v => Option(ws => v(ws).merge(onMessage(ws)))))
        case OnError(onError)     => s.copy(onError = s.onError.fold(Option(onError))(v => Option(c => v(c) &> onError(c))))
        case OnClose(onClose)     => s.copy(onClose = s.onClose.fold(Option(onClose))(v => Option(c => v(c) &> onClose(c))))
        case Decoder(config)      => s.copy(decoderConfig = s.decoderConfig ++ config)
        case Protocol(config)     => s.copy(protocolConfig = s.protocolConfig ++ config)
        case Concat(a, b)         => loop(b, loop(a, s))
      }

    loop(socket, iSettings)
  }
}
