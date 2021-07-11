package zhttp.channel

import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext}

/**
 * Domain to model all the main operations that are available on a Channel. The type `A` does not represent the output
 * of the Operation. By default all operations complete with a `Unit` so the type parameter for output becomes redundant
 * (And hence removed). The type `A` represents the type of the data on which the operation is valid.
 *
 * TODO: Add Benchmarks and possibly add stack-safety.
 */
sealed trait Operation[+A, +S] { self =>
  import Operation._

  def combine[A1 >: A, S1 >: S](other: Operation[A1, S1]): Operation[A1, S1] = Operation.Combine(self, other)

  def ++[A1 >: A, S1 >: S](other: Operation[A1, S1]): Operation[A1, S1] = self combine other

  def map[B](aa: A => B): Operation[B, S] = FMap(self, aa)
}

object Operation {
  val close: Operation[Nothing, Nothing]                                  = run(_.close())
  val read: Operation[Nothing, Nothing]                                   = run(_.read())
  val flush: Operation[Nothing, Nothing]                                  = run(_.flush())
  val empty: Operation[Nothing, Nothing]                                  = run(_ => ())
  val startAutoRead: Operation[Nothing, Nothing]                          = run(_.channel().config().setAutoRead(true))
  val stopAutoRead: Operation[Nothing, Nothing]                           = run(_.channel().config().setAutoRead(false))
  def write[A](a: A): Operation[A, Nothing]                               = Write(a)
  def save[S](s: S): Operation[Nothing, S]                                = Save(s)
  def run(cb: JChannelHandlerContext => Any): Operation[Nothing, Nothing] = Run(cb)
  def combine[A, S](list: Iterable[Operation[A, S]]): Operation[A, S]     =
    list.fold(Operation.empty)((a, b) => a ++ b)

  private[zhttp] final case class Write[A](data: A)                                            extends Operation[A, Nothing]
  private[zhttp] final case class Combine[A, S](self: Operation[A, S], other: Operation[A, S]) extends Operation[A, S]
  private[zhttp] final case class FMap[A, B, S](self: Operation[A, S], ab: A => B)             extends Operation[B, S]
  private[zhttp] final case class Run(cb: JChannelHandlerContext => Any)                       extends Operation[Nothing, Nothing]
  private[zhttp] final case class Save[S](cb: S)                                               extends Operation[Nothing, S]
}
