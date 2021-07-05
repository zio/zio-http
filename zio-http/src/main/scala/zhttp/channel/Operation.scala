package zhttp.channel

import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext}
import zio._
import zhttp.socket.SocketApp

/**
 * Domain to model all the main operations that are available on a Channel. The type `A` does not represent the output
 * of the Operation. By default all operations complete with a `Unit` so the type parameter for output becomes redundant
 * (And hence removed). The type `A` represents the type of the data on which the operation is valid.
 *
 * TODO: Add Benchmarks and possibly add stack-safety.
 */
sealed trait Operation[-R, +E, +A] { self =>
  import Operation._
  def ++[R1 <: R, E1 >: E, A1 >: A](other: Operation[R1, E1, A1]): Operation[R1, E1, A1] =
    Operation.Combine(self, other)

  def map[B](ab: A => B): Operation[R, E, B] = Operation.FMap(self, ab)

  private[zhttp] def execute(ctx: JChannelHandlerContext): Unit =
    self match {
      case Write(data)          => ctx.write(data): Unit
      case Read                 => ctx.read(): Unit
      case Flush                => ctx.flush(): Unit
      case Close                => ctx.close(): Unit
      case Empty                => ()
      case StartAutoRead        => ctx.channel().config().setAutoRead(true): Unit
      case StopAutoRead         => ctx.channel().config().setAutoRead(false): Unit
      case Effect(_)            => ??? // TODO: @amitksingh1490
      case Combine(self, other) => self.execute(ctx); other.execute(ctx)
      case Socket(_)            => ??? // TODO: @amitksingh1490
      case FMap(self, ab)       =>
        self match {
          case Write(data)          => ctx.write(ab(data)): Unit
          case Combine(self, other) => (self.map(ab) ++ other.map(ab)).execute(ctx)
          case FMap(self, bc)       => self.map(bc.compose(ab)).execute(ctx)
          case msg                  => msg.execute(ctx)
        }
    }
}

object Operation {
  def close: Operation[Any, Nothing, Nothing]                            = Close
  def read: Operation[Any, Nothing, Nothing]                             = Read
  def flush: Operation[Any, Nothing, Nothing]                            = Flush
  def write[A](a: A): Operation[Any, Nothing, A]                         = Write(a)
  def empty: Operation[Any, Nothing, Nothing]                            = Empty
  def startAutoRead: Operation[Any, Nothing, Nothing]                    = StartAutoRead
  def stopAutoRead: Operation[Any, Nothing, Nothing]                     = StopAutoRead
  def fromEffect[R, E](effect: ZIO[R, E, Any]): Operation[R, E, Nothing] = Effect(effect)
  def socket[R, E](app: SocketApp[R, E]): Operation[R, E, Nothing]       = Socket(app)

  case object Empty                                                                extends Operation[Any, Nothing, Nothing]
  case object Read                                                                 extends Operation[Any, Nothing, Nothing]
  case object Flush                                                                extends Operation[Any, Nothing, Nothing]
  case object Close                                                                extends Operation[Any, Nothing, Nothing]
  case class Write[A](data: A)                                                     extends Operation[Any, Nothing, A]
  case class Effect[R, E, A](effect: ZIO[R, E, Any])                               extends Operation[R, E, Nothing]
  case object StartAutoRead                                                        extends Operation[Any, Nothing, Nothing]
  case object StopAutoRead                                                         extends Operation[Any, Nothing, Nothing]
  case class FMap[R, E, A, B](self: Operation[R, E, A], ab: A => B)                extends Operation[R, E, B]
  case class Combine[R, E, A](self: Operation[R, E, A], other: Operation[R, E, A]) extends Operation[R, E, A]
  case class Socket[R, E](app: SocketApp[R, E])                                    extends Operation[R, E, Nothing]
}
