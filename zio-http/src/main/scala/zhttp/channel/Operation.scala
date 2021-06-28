package zhttp.channel

import io.netty.buffer.{ByteBuf => JByteBuf}
import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext}
import io.netty.handler.codec.http.{
  DefaultHttpContent => JDefaultHttpContent,
  DefaultHttpResponse => JDefaultHttpResponse,
  DefaultLastHttpContent => JDefaultLastHttpContent,
  HttpVersion => JHttpVersion,
}
import zhttp.http.{Header, Status}

/**
 * Domain to model all the main operations that are available on a Channel
 */
sealed trait Operation[+A] { self =>
  def ++[A1 >: A](other: Operation[A1]): Operation[A1] =
    Operation.Combine(self, other)

  private[zhttp] def execute(ctx: JChannelHandlerContext)(implicit ev: A <:< JByteBuf): Unit = {
    self match {
      case Operation.Response(status, headers) =>
        ctx.write(
          new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, status.toJHttpStatus, Header.disassemble(headers)),
        )
      case Operation.Content(data)             => ctx.write(new JDefaultHttpContent(data))
      case Operation.End(data)                 => ctx.write(new JDefaultLastHttpContent(data))
      case Operation.Read                      => ctx.read()
      case Operation.Flush                     => ctx.flush()
      case Operation.Close                     => ctx.close()
      case Operation.Empty                     => ()
      case Operation.Combine(self, other)      =>
        self.execute(ctx)
        other.execute(ctx)
    }
    ()
  }
}
object Operation           {
  def close: Operation[Nothing]                                                             = Close
  def read: Operation[Nothing]                                                              = Read
  def flush: Operation[Nothing]                                                             = Flush
  def content[A](a: A): Operation[A]                                                        = Content(a)
  def end[A](a: A): Operation[A]                                                            = End(a)
  def empty: Operation[Nothing]                                                             = Empty
  def response(status: Status = Status.OK, headers: List[Header] = Nil): Operation[Nothing] =
    Response(status, headers)

  case object Empty                                              extends Operation[Nothing]
  case class Response(status: Status, headers: List[Header])     extends Operation[Nothing]
  case class Content[A](data: A)                                 extends Operation[A]
  case class End[A](data: A)                                     extends Operation[A]
  case object Read                                               extends Operation[Nothing]
  case object Flush                                              extends Operation[Nothing]
  case object Close                                              extends Operation[Nothing]
  case class Combine[A](self: Operation[A], other: Operation[A]) extends Operation[A]
}
