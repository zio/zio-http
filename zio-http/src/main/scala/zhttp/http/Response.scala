package zhttp.http

import zhttp.socket.Socket._
import zhttp.socket.{DecoderConfig, ProtocolConfig, WebSocketFrame}
import zio.ZIO
import zio.stream.ZStream

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response extends ResponseOps {
  // Constructors
  final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]

  case class SocketResponse[-R, +E](
    onTimeout: Option[ZIO[R, Nothing, Unit]] = None,
    onOpen: Option[Connection => ZStream[R, E, WebSocketFrame]] = None,
    onMessage: Option[WebSocketFrame => ZStream[R, E, WebSocketFrame]] = None,
    onError: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
    onClose: Option[Connection => ZIO[R, Nothing, Unit]] = None,
    protocolConfig: ProtocolConfig = ProtocolConfig.empty,
    decoderConfig: DecoderConfig = DecoderConfig.empty,
  ) extends Response[R, E]
}
