package zhttp.experiment

import zhttp.socket.SocketApp
import zio.stream.ZStream

private[zhttp] sealed trait Content[-R, +E, +A] {}

object Content {
  case object Empty                                             extends Content[Any, Nothing, Nothing]
  final case class Complete[A](data: A)                         extends Content[Any, Nothing, A]
  final case class Streaming[R, E, A](stream: ZStream[R, E, A]) extends Content[R, E, A]
  final case class FromSocket[R, E](app: SocketApp[R, E])       extends Content[R, E, Nothing]

  def empty: Content[Any, Nothing, Nothing]                                = Empty
  def complete[A](data: A): Content[Any, Nothing, A]                       = Complete(data)
  def fromStream[R, E, A](stream: ZStream[R, E, A]): Content[R, E, A]      = Streaming(stream)
  def fromSocket[R, E](socketApp: SocketApp[R, E]): Content[R, E, Nothing] = FromSocket(socketApp)
}
