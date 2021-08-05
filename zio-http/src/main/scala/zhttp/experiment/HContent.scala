package zhttp.experiment

import zio.ZIO
import zio.stream.ZStream

sealed trait HContent[-R, +E, +A] {}

object HContent {
  case object Empty                                               extends HContent[Any, Nothing, Nothing]
  case class Complete[A](data: A)                                 extends HContent[Any, Nothing, A]
  case class Streaming[R, E, A](stream: ZStream[R, Option[E], A]) extends HContent[R, E, A]
  case class FromChannel[R, E, A](ch: Channel[R, E, A, A])        extends HContent[R, E, A]

  def empty: HContent[Any, Nothing, Nothing]                                  = Empty
  def complete[A](data: A): HContent[Any, Nothing, A]                         = Complete(data)
  def from[R, E, A](ch: Channel[R, E, A, A]): HContent[R, E, A]               = FromChannel(ch)
  def fromStream[R, E, A](stream: ZStream[R, E, A]): HContent[R, E, A]        = Streaming(stream.mapError(Option(_)))
  def from[R, E, A](cb: (A, Context[A]) => ZIO[R, E, Any]): HContent[R, E, A] = from(Channel[R, E, A, A](onRead = cb))
  def echo[A]: HContent[Any, Nothing, A]                                      = HContent.from { (msg: A, context: Context[A]) =>
    for {
      _ <- context.write(msg)
      _ <- context.read
    } yield ()
  }
}
