package zio.http.netty.client

import io.netty.buffer.ByteBufUtil
import io.netty.channel._
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zio.http.Body.UnsafeAsync
import zio.http.netty.{NettyFutureExecutor, NettyRuntime}
import zio.{Chunk, Promise, Trace, Unsafe}

final class ClientResponseStreamHandler(
  val callback: UnsafeAsync,
  zExec: NettyRuntime,
  onComplete: Promise[Throwable, ChannelState],
  keepAlive: Boolean,
) extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  private val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))
    callback(ctx.channel(), chunk, isLast)
    if (isLast) {
      ctx.channel().pipeline().remove(self)

      if (keepAlive)
        zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(onComplete.succeed(ChannelState.Reusable))(
          unsafeClass,
        )
      else {
        zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
          NettyFutureExecutor
            .executed(ctx.close())
            .as(ChannelState.Invalid)
            .exit
            .flatMap(onComplete.done(_)),
        )(unsafeClass)
      }
    }: Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(onComplete.fail(cause))(unsafeClass)
  }
}
