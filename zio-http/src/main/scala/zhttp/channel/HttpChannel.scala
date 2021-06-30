package zhttp.channel

import io.netty.channel.{ChannelInboundHandlerAdapter => JChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpObject => JHttpObject}
import zhttp.core.{JChannelHandler, JChannelHandlerContext}
import zhttp.http.{Http, HttpResult}
import zhttp.service.UnsafeChannelExecutor

case class HttpChannel[-R, +E, -A, +B](asHttp: Http[R, E, Event[A], Operation[B]]) { self =>
  def *>[R1 <: R, E1 >: E, A1 <: A, B1](other: HttpChannel[R1, E1, A1, B1]): HttpChannel[R1, E1, A1, B1] =
    HttpChannel(self.asHttp *> other.asHttp)
}

object HttpChannel {
  def collect[A]: MkCollect[A] = new MkCollect[A](())

  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[B](pf: PartialFunction[Event[A], Operation[B]]): HttpChannel[Any, Nothing, A, B] =
      HttpChannel(Http.collect[Event[A]](pf))
  }

  private[zhttp] def compile[R, E <: Throwable](
    zExec: UnsafeChannelExecutor[R],
    channel: HttpChannel[R, E, JHttpObject, JHttpObject],
  ): JChannelHandler =
    new JChannelInboundHandlerAdapter {

      private def execute(ctx: JChannelHandlerContext, event: Event[JHttpObject]): Unit = {
        channel.asHttp.execute(event).evaluate match {
          case HttpResult.Success(a)      => a.execute(ctx)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e)
          case HttpResult.Effect(program) =>
            zExec.unsafeExecute(ctx, program) { _ =>
              ???
            }
          case HttpResult.Empty           => ()
        }
        ()
      }

      override def channelReadComplete(ctx: JChannelHandlerContext): Unit =
        execute(ctx, Event.Complete)

      override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit =
        execute(ctx, Event.Read(msg.asInstanceOf[JHttpObject]))

      override def channelRegistered(ctx: JChannelHandlerContext): Unit = {
        ctx.channel().config().setAutoRead(false)
        ctx.read()
        ()
      }
    }

  def empty: HttpChannel[Any, Nothing, Any, Nothing] = HttpChannel.collect(_ => Operation.close)
}
