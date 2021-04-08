package zhttp.socket

import zio._
import zio.stream.ZStream

import java.net.{SocketAddress => JSocketAddress}
// Todo add unit tests
sealed trait Socket[-R, +E] { self =>
  def <+>[R1 <: R, E1 >: E](other: Socket[R1, E1]): Socket[R1, E1] = Socket.Concat(self, other)
  def settings: Socket.Settings[R, E]                              = Socket.settings(self)
}

object Socket {
  type Connection = JSocketAddress
  type Cause      = Option[Throwable]

  // [R, Nothing, Boolean]
  // Boolean, [R, E, Unit]
  case class Settings[-R, +E](
    subProtocol: Option[String] = None,
    // Triggered when the socket is upgraded
    // there is a failure, ctx.close() is called
    onOpen: Connection => ZIO[R, E, Unit] = (_: Connection) => ZIO.unit,
    // There is a failure, ctx.close() is called.
    onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame] = (_: WebSocketFrame) => ZStream.empty,
    // No error channel because there is nothing left to handle it.
    // Channel should get closed after execution.
    onError: Throwable => ZIO[R, Nothing, Unit] = (_: Throwable) => ZIO.unit,
    // Last thing in the pipeline, so it can't fail.
    // TODO: Http Context may be required in certain cases
    onClose: (Connection, Cause) => ZIO[R, Nothing, Unit] = (_: Connection, _: Cause) => ZIO.unit,
  )

  private case class SubProtocol(name: String)                                                   extends Socket[Any, Nothing]
  private case class OnOpen[R, E](onOpen: Connection => ZIO[R, E, Unit])                         extends Socket[R, E]
  private case class OnMessage[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]) extends Socket[R, E]
  private case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])                     extends Socket[R, Nothing]
  private case class OnClose[R](onClose: (Connection, Cause) => ZIO[R, Nothing, Unit])           extends Socket[R, Nothing]
  private case class Concat[R, E](a: Socket[R, E], b: Socket[R, E])                              extends Socket[R, E]

  def subProtocol(name: String): Socket[Any, Nothing]                                                        = SubProtocol(name)
  def open[R, E](onOpen: Connection => ZIO[R, E, Unit]): Socket[R, E]                                        = OnOpen(onOpen)
  def message[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]): Socket[R, E]                =
    OnMessage(onMessage)
  def collect[R, E](onMessage: PartialFunction[WebSocketFrame, ZStream[R, E, WebSocketFrame]]): Socket[R, E] =
    message(ws => if (onMessage.isDefinedAt(ws)) onMessage(ws) else ZStream.empty)
  def error[R](onError: Throwable => ZIO[R, Nothing, Unit]): Socket[R, Nothing]                              = OnError(onError)
  def close[R](onClose: (Connection, Cause) => ZIO[R, Nothing, Unit]): Socket[R, Nothing]                    = OnClose(onClose)

  def settings[R, E](ss: Socket[R, E], s: Settings[R, E] = Settings()): Settings[R, E] = ss match {
    case SubProtocol(name)    => s.copy(Option(name))
    case OnOpen(onOpen)       => s.copy(onOpen = onOpen)
    case OnMessage(onMessage) => s.copy(onMessage = ws => s.onMessage(ws).merge(onMessage(ws)))
    case OnError(onError)     => s.copy(onError = onError)
    case OnClose(onClose)     => s.copy(onClose = onClose)
    case Concat(a, b)         => settings(b, settings(a, s))
  }
}
