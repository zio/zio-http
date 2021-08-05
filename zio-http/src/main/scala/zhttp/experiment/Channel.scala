package zhttp.experiment

import zio.ZIO

trait Channel[-R, +E, -A, +B] {
  def message(message: A, context: Context[B]): ZIO[R, E, Any]
  def error(cause: Throwable, context: Context[B]): ZIO[R, E, Any]
  def complete(context: Context[B]): ZIO[R, E, Any]
}

object Channel {
  def apply[R, E, A, B](
    onRead: (A, Context[B]) => ZIO[R, E, Any] = (a: A, b: Context[B]) => b.fireChannelRead(a),
    onError: (Throwable, Context[B]) => ZIO[R, E, Any] = (a: Throwable, b: Context[B]) => b.fireExceptionCaught(a),
    onComplete: Context[B] => ZIO[R, E, Any] = (b: Context[B]) => b.fireChannelReadComplete(),
  ): Channel[R, E, A, B] = new Channel[R, E, A, B] {
    override def message(message: A, context: Context[B]): ZIO[R, E, Any]     = onRead(message, context)
    override def error(cause: Throwable, context: Context[B]): ZIO[R, E, Any] = onError(cause, context)
    override def complete(context: Context[B]): ZIO[R, E, Any]                = onComplete(context)
  }
}
