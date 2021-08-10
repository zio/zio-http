package zhttp.experiment

import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.HttpObject
import zhttp.service.UnsafeChannelExecutor
import zio.{UIO, ZIO}

trait Channel[-R, +E, -A, +B] {
  def message(message: A, context: Context[B]): ZIO[R, E, Unit]
  def error(cause: Throwable, context: Context[B]): ZIO[R, E, Unit]

  def compile[R1 <: R](
    zExec: UnsafeChannelExecutor[R1],
  )(implicit evE: E <:< Throwable): ChannelHandler =
    new ChannelInboundHandlerAdapter {
      private val self = this.asInstanceOf[Channel[R1, Throwable, HttpObject, HttpObject]]
      override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
        zExec.unsafeExecute_(ctx)(msg match {
          case msg: HttpObject => self.message(msg, Context(ctx))
          case _               => UIO(ctx.fireChannelRead(msg))
        })
      }

      override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
        zExec.unsafeExecute_(ctx)(self.error(cause, Context(ctx)))
    }
}

object Channel {
  def apply[R, E, A, B](
    onRead: (A, Context[B]) => ZIO[R, E, Any] = (a: A, b: Context[B]) => b.fireChannelRead(a),
    onError: (Throwable, Context[B]) => ZIO[R, E, Any] = (a: Throwable, b: Context[B]) => b.fireExceptionCaught(a),
  ): Channel[R, E, A, B] = new Channel[R, E, A, B] {
    override def message(message: A, context: Context[B]): ZIO[R, E, Unit]     = onRead(message, context).unit
    override def error(cause: Throwable, context: Context[B]): ZIO[R, E, Unit] = onError(cause, context).unit
  }
}
