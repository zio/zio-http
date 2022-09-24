package zio.http.netty.server

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zio.Chunk
import zio.http.Body
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] final class ServerAsyncBodyHandler(val async: Body.UnsafeAsync)
    extends SimpleChannelInboundHandler[HttpContent](true) {
  self =>

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))
    async(ctx.channel(), chunk, isLast)
    if (isLast) ctx.channel().pipeline().remove(self): Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }
}
