package zhttp.http

import zio._
import zhttp.socket.WebSocketFrame
import zio.stream.ZStream

import scala.annotation.tailrec

sealed trait SocketServer[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: SocketServer[R1, E1]): SocketServer[R1, E1] = SocketServer.Concat(self, other)
}

object SocketServer {
  type Connection
  type Cause = Option[Throwable]

  // [R, Nothing, Boolean]
  // Boolean, [R, E, Unit]
  case class Settings[-R, +E](
    subProtocol: Option[String] = None,
    // Triggered when the socket is upgraded
    // there is a failure, ctx.close() is called
    onOpen: Connection => ZIO[R, E, Unit],
    // There is a failure, ctx.close() is called.
    onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame],
    // No error channel because there is nothing left to handle it.
    // Channel should get closed after execution.
    onError: Throwable => ZIO[R, Nothing, Unit],
    // Last thing in the pipeline, so it can't fail.
    onClose: (Connection, Cause) => ZIO[R, Nothing, Unit],
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

  @tailrec
  def settings[R, E](ss: SocketServer[R, E]): Settings[R, E] = ss match {
    case SubProtocol(name)    => settings(subProtocol(name))
    case OnOpen(onOpen)       => settings(open(onOpen))
    case OnMessage(onMessage) => settings(message(onMessage))
    case OnError(onError)     => settings(error(onError))
    case OnClose(onClose)     => settings(close(onClose))
    case Concat(a, b)         => settings(Concat(a, b))
  }
}
