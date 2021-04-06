package zhttp.http

import zhttp.socket.WebSocketFrame
import zio._
import zio.stream.ZStream
import java.net.{SocketAddress => JSocketAddress}

sealed trait SocketServer[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: SocketServer[R1, E1]): SocketServer[R1, E1] = SocketServer.Concat(self, other)
  def settings: SocketServer.Settings[R, E]                                   = SocketServer.settings(self)
}

object SocketServer {
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

  private case class SubProtocol(name: String)                                         extends SocketServer[Any, Nothing]
  private case class OnOpen[R, E](onOpen: Connection => ZIO[R, E, Unit])               extends SocketServer[R, E]
  private case class OnMessage[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame])
      extends SocketServer[R, E]
  private case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])           extends SocketServer[R, Nothing]
  private case class OnClose[R](onClose: (Connection, Cause) => ZIO[R, Nothing, Unit]) extends SocketServer[R, Nothing]
  private case class Concat[R, E](a: SocketServer[R, E], b: SocketServer[R, E])        extends SocketServer[R, E]

  def subProtocol(name: String): SocketServer[Any, Nothing]                                         = SubProtocol(name)
  def open[R, E](onOpen: Connection => ZIO[R, E, Unit]): SocketServer[R, E]                         = OnOpen(onOpen)
  def message[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]): SocketServer[R, E] = OnMessage(
    onMessage,
  )
  def error[R](onError: Throwable => ZIO[R, Nothing, Unit]): SocketServer[R, Nothing]               = OnError(onError)
  def close[R](onClose: (Connection, Cause) => ZIO[R, Nothing, Unit]): SocketServer[R, Nothing]     = OnClose(onClose)

  def settings[R, E](ss: SocketServer[R, E], s: Settings[R, E] = Settings()): Settings[R, E] = ss match {
    case SubProtocol(name)    => s.copy(Option(name))
    case OnOpen(onOpen)       => s.copy(onOpen = onOpen)
    case OnMessage(onMessage) => s.copy(onMessage = onMessage)
    case OnError(onError)     => s.copy(onError = onError)
    case OnClose(onClose)     => s.copy(onClose = onClose)
    case Concat(a, b)         => settings(b, settings(a, s))
  }
}
