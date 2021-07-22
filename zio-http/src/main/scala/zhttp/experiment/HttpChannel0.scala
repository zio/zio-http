package zhttp.experiment

import io.netty.channel.{
  ChannelHandler => JChannelHandler,
  ChannelHandlerContext => JChannelHandlerContext,
  SimpleChannelInboundHandler => JSimpleChannelInboundHandler,
}
import io.netty.handler.codec.http.{HttpObject => JHttpObject}
import zhttp.service.UnsafeChannelExecutor
import zio.{UIO, ZIO}

object HttpChannel0 {

  case class Context[-A](asJava: JChannelHandlerContext) {
    def read: UIO[Unit]        = UIO(asJava.read(): Unit)
    def write(a: A): UIO[Unit] = UIO(asJava.write(a): Unit)
    def flush: UIO[Unit]       = UIO(asJava.flush(): Unit)
    def close: UIO[Unit]       = UIO(asJava.close(): Unit)
  }

  case class HttpChannel[-R, +E, -A, +B](execute: (Event[A], Context[B]) => ZIO[R, E, Any]) {}

  object HttpChannel {
    trait Channel[-R, +E, -A, +B] {
      def execute(event: Event[A], context: Context[B]): ZIO[R, E, Any]
    }

    private[zhttp] def compile[R, A, B](
      zExec: UnsafeChannelExecutor[R],
      adapter: HttpChannel[R, Throwable, JHttpObject, JHttpObject],
    ): JChannelHandler =
      new JSimpleChannelInboundHandler[JHttpObject] { self =>
        override def channelRegistered(ctx: JChannelHandlerContext): Unit = {
          ctx.channel().config().setAutoRead(false)
          zExec.unsafeExecute_(ctx) {
            adapter.execute(Event.Register, Context(ctx))
          }
        }

        override def channelRead0(ctx: JChannelHandlerContext, msg: JHttpObject): Unit =
          zExec.unsafeExecute_(ctx) {
            adapter.execute(Event.Read(msg), Context(ctx))
          }

        override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit =
          zExec.unsafeExecute_(ctx) {
            adapter.execute(Event.Failure(cause), Context(ctx))
          }
      }
  }
}
