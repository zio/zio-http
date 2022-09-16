package zio.http.service

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zio.http.Body.UnsafeAsync
import zio.{Chunk, Promise, Unsafe}

final class ClientResponseStreamHandler(
  val callback: UnsafeAsync,
  zExec: HttpRuntime[Any],
  onComplete: Promise[Throwable, Unit],
) extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  override def channelRead0(
    ctx: Ctx,
    msg: HttpContent,
  ): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))
    callback(ctx.channel(), chunk, isLast)
    if (isLast) {
      ctx.channel().pipeline().remove(self)
      zExec.runUninterruptible(onComplete.succeed(()))(ctx, Unsafe.unsafe)
    }: Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    zExec.runUninterruptible(onComplete.succeed(()))(ctx, Unsafe.unsafe)
  }
}
