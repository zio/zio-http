package zio.http.netty.client

import io.netty.buffer.ByteBufUtil
import io.netty.channel._
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zio.Chunk
import zio.http.Body.UnsafeAsync
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final class ClientResponseStreamHandler(val callback: UnsafeAsync)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  override def channelRead0(
    ctx: ChannelHandlerContext,
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
