package zhttp.channel

import io.netty.buffer.{ByteBuf => JByteBuf}
import io.netty.channel.{ChannelInboundHandlerAdapter => JChannelInboundHandlerAdapter}
import zhttp.core.{JChannelHandler, JChannelHandlerContext}
import zhttp.http.{Http, HttpResult}
import zhttp.service.UnsafeChannelExecutor
import zio._

object HttpChannel {
  def collect[A]: MkCollect[A] = new MkCollect[A](())
  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[B](pf: PartialFunction[Event[A], Operation[B]]): HttpChannel[Any, Nothing, A, B] =
      Http.collect[Event[A]] { case a if pf.isDefinedAt(a) => pf(a) }
  }

  private[zhttp] def compile[R, E <: Throwable](
    zExec: UnsafeChannelExecutor[R],
    channel: HttpChannel[R, E, JByteBuf, JByteBuf],
  ): JChannelHandler =
    new JChannelInboundHandlerAdapter {
      private def execute(ctx: JChannelHandlerContext, event: Event[JByteBuf]): Unit = {
        val evaluate = channel.execute(event).evaluate

        evaluate match {
          case HttpResult.Success(a)      => a.execute(ctx)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e)
          case HttpResult.Effect(program) =>
            zExec.unsafeExecute(ctx, program) {
              case Exit.Success(a)     => a.execute(ctx)
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(Some(e)) => ctx.fireExceptionCaught(e)
                  case Some(None)    => ()
                  case None          => ctx.close()
                }
            }
          case HttpResult.Empty           => ()
        }
        ()
      }

      override def channelReadComplete(ctx: JChannelHandlerContext): Unit =
        execute(ctx, Event.Complete)

      override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit =
        execute(ctx, Event.decode(msg))

      override def channelRegistered(ctx: JChannelHandlerContext): Unit = {
        ctx.channel().config().setAutoRead(false)
        ctx.read()
        ()
      }
    }

  def empty: HttpChannel[Any, Nothing, Any, Nothing] = HttpChannel.collect(_ => Operation.close)
}
