package zio.http.service

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zio.Chunk
import zio.http.Body.UnsafeAsync

final class ClientResponseStreamHandler(val callback: UnsafeAsync)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  override def channelRead0(
    ctx: Ctx,
    msg: HttpContent,
  ): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))
    callback(ctx.channel(), chunk, isLast)
    if (isLast) ctx.channel().pipeline().remove(self): Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }
}
