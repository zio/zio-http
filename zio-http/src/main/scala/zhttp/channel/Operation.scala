package zhttp.channel

import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext}

/**
 * Domain to model all the main operations that are available on a Channel. The type `A` does not represent the output
 * of the Operation. By default all operations complete with a `Unit` so the type parameter for output becomes redundant
 * (And hence removed). The type `A` represents the type of the data on which the operation is valid.
 *
 * TODO: Add Benchmarks and possibly add stack-safety.
 */
sealed trait Operation[+A] { self =>
  import Operation._

  def combine[A1 >: A](other: Operation[A1]): Operation[A1] = Operation.Combine(self, other)

  def ++[A1 >: A](other: Operation[A1]): Operation[A1] = self combine other

  def map[B](aa: A => B): Operation[B] = FMap(self, aa)

  private[zhttp] def execute(ctx: JChannelHandlerContext): Unit =
    self match {
      case Write(data)          => ctx.write(data): Unit
      case Combine(self, other) => self.execute(ctx); other.execute(ctx);
      case Modify(cb)           => cb(ctx): Unit
      case FMap(self, ab)       =>
        (self match {
          case Write(data)          => Write(ab(data))
          case Combine(self, other) => Combine(self.map(ab), other.map(ab))
          case FMap(self, bc)       => self.map(bc.andThen(ab))
          case m @ Modify(_)        => m
        }).execute(ctx)
    }
}

object Operation {
  val close: Operation[Nothing]                                     = modify(_.close())
  val read: Operation[Nothing]                                      = modify(_.read())
  val flush: Operation[Nothing]                                     = modify(_.flush)
  val empty: Operation[Nothing]                                     = modify(_ => ())
  val startAutoRead: Operation[Nothing]                             = modify(_.channel().config().setAutoRead(true))
  val stopAutoRead: Operation[Nothing]                              = modify(_.channel().config().setAutoRead(false))
  def write[A](a: A): Operation[A]                                  = Write(a)
  def modify(cb: JChannelHandlerContext => Any): Operation[Nothing] = Modify(cb)

  case class Write[A](data: A)                                   extends Operation[A]
  case class Combine[A](self: Operation[A], other: Operation[A]) extends Operation[A]
  case class FMap[A, B](self: Operation[A], ab: A => B)          extends Operation[B]
  case class Modify(cb: JChannelHandlerContext => Any)           extends Operation[Nothing]
}
