package zhttp.channel

import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext}
import zio._

/**
 * Domain to model all the main operations that are available on a Channel
 */
sealed trait Operation[-R, +E, +A] { self =>
  import Operation._

  def ++[R1 <: R, E1 >: E, A1 >: A](other: Operation[R1, E1, A1]): Operation[R1, E1, A1] =
    Operation.Combine(self, other)

  private[zhttp] def execute(ctx: JChannelHandlerContext): Unit =
    self match {
      case Write(data)          => ctx.write(data): Unit
      case Read                 => ctx.read(): Unit
      case Flush                => ctx.flush(): Unit
      case Close                => ctx.close(): Unit
      case Empty                => ()
      case StartAutoRead        => ctx.channel().config().setAutoRead(true): Unit
      case StopAutoRead         => ctx.channel().config().setAutoRead(false): Unit
      case Effect(_)            => ???
      case Combine(self, other) => self.execute(ctx); other.execute(ctx)
    }
}

object Operation {
  def close: Operation[Any, Nothing, Nothing]                       = Close
  def read: Operation[Any, Nothing, Nothing]                        = Read
  def flush: Operation[Any, Nothing, Nothing]                       = Flush
  def write[A](a: A): Operation[Any, Nothing, A]                    = Write(a)
  def empty: Operation[Any, Nothing, Nothing]                       = Empty
  def startAutoRead: Operation[Any, Nothing, Nothing]               = StartAutoRead
  def stopAutoRead: Operation[Any, Nothing, Nothing]                = StopAutoRead
  def fromEffect[R, E, A](effect: ZIO[R, E, A]): Operation[R, E, A] = Effect(effect)

  case object Empty                                                                extends Operation[Any, Nothing, Nothing]
  case object Read                                                                 extends Operation[Any, Nothing, Nothing]
  case object Flush                                                                extends Operation[Any, Nothing, Nothing]
  case object Close                                                                extends Operation[Any, Nothing, Nothing]
  case class Write[A](data: A)                                                     extends Operation[Any, Nothing, A]
  case class Combine[R, E, A](self: Operation[R, E, A], other: Operation[R, E, A]) extends Operation[R, E, A]
  case class Effect[R, E, A](effect: ZIO[R, E, A])                                 extends Operation[R, E, A]
  case object StartAutoRead                                                        extends Operation[Any, Nothing, Nothing]
  case object StopAutoRead                                                         extends Operation[Any, Nothing, Nothing]
}
