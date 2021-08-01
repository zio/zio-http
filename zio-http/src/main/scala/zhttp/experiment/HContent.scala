package zhttp.experiment

import zio.ZIO
import zio.stream.ZStream

sealed trait HContent[-R, +E] {}

object HContent {
  case object Empty                                               extends HContent[Any, Nothing]
  case class Complete(data: CByte)                                extends HContent[Any, Nothing]
  case class Streaming[R, E](stream: ZStream[R, Option[E], Byte]) extends HContent[R, E]
  case class FromChannel[R, E](ch: Channel[R, E, CByte, CByte])   extends HContent[R, E]

  def empty: HContent[Any, Nothing]                                             = Empty
  def from(data: CByte): HContent[Any, Nothing]                                 = Complete(data)
  def from[R, E](ch: Channel[R, E, CByte, CByte]): HContent[R, E]               = FromChannel(ch)
  def from[R, E](stream: ZStream[R, E, Byte]): HContent[R, E]                   = Streaming(stream.mapError(Option(_)))
  def from[R, E](cb: (CByte, Context[CByte]) => ZIO[R, E, Any]): HContent[R, E] = from(Channel(onRead = cb))
  def echo: HContent[Any, Nothing]                                              = HContent.from { (msg, context) =>
    for {
      _ <- context.write(msg)
      _ <- context.read
    } yield ()
  }
}
