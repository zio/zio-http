package zhttp.channel

import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext}
import io.netty.handler.codec.http.{HttpObject => JHttpObject}

/**
 * Domain to model all the main operations that are available on a Channel
 */
sealed trait Operation[+A] { self =>
  def ++[A1 >: A](other: Operation[A1]) = Operation.Combine(self, other)

  private[zhttp] def execute(ctx: JChannelHandlerContext): Unit = {
    self match {
      case Operation.Write(data)          => ctx.write(data)
      case Operation.Read                 => ctx.read()
      case Operation.Flush                => ctx.flush()
      case Operation.Close                => ctx.close()
      case Operation.Empty                => ()
      case Operation.StartAutoRead        => ctx.channel().config().setAutoRead(true)
      case Operation.StopAutoRead         => ctx.channel().config().setAutoRead(false)
      case Operation.Combine(self, other) =>
        self.execute(ctx)
        other.execute(ctx)
    }
    ()
  }
}
object Operation           {
  def close: Operation[Nothing]         = Close
  def read: Operation[Nothing]          = Read
  def flush: Operation[Nothing]         = Flush
  def write[A](a: A): Operation[A]      = Write(a)
  def empty: Operation[Nothing]         = Empty
  def startAutoRead: Operation[Nothing] = StartAutoRead
  def stopAutoRead: Operation[Nothing]  = StopAutoRead

  case object Empty                                              extends Operation[Nothing]
  case object Read                                               extends Operation[Nothing]
  case object Flush                                              extends Operation[Nothing]
  case object Close                                              extends Operation[Nothing]
  case class Write[A](data: A)                                   extends Operation[A]
  case class Combine[A](self: Operation[A], other: Operation[A]) extends Operation[A]
  case object StartAutoRead                                      extends Operation[Nothing]
  case object StopAutoRead                                       extends Operation[Nothing]

  type ServerResponse = JHttpObject
}
