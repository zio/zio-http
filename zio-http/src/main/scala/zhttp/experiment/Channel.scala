package zhttp.experiment

import io.netty.channel.ChannelHandler
import zio.ZIO

trait Channel[-R, +E, -A, +B] {
  def message(message: A, context: Context[B]): ZIO[R, E, Any]
  def error(cause: Throwable, context: Context[B]): ZIO[R, E, Any]
  def complete(context: Context[B]): ZIO[R, E, Any]

  def compile: ChannelHandler = ???
}

object Channel {
  def apply[R, E](
    onRead: (CByte, Context[CByte]) => ZIO[R, E, Any] = (a: CByte, b: Context[CByte]) => b.fireChannelRead(a),
    onError: (Throwable, Context[CByte]) => ZIO[R, E, Any] = (a: Throwable, b: Context[CByte]) =>
      b.fireExceptionCaught(a),
    onComplete: Context[CByte] => ZIO[R, E, Any] = (b: Context[CByte]) => b.fireChannelReadComplete(),
  ): Channel[R, E, CByte, CByte] = new Channel[R, E, CByte, CByte] {
    override def message(message: CByte, context: Context[CByte]): ZIO[R, E, Any] = onRead(message, context)
    override def error(cause: Throwable, context: Context[CByte]): ZIO[R, E, Any] = onError(cause, context)
    override def complete(context: Context[CByte]): ZIO[R, E, Any]                = onComplete(context)
  }
}
