package zio.http.service

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent, LastHttpContent}
import zhttp.service.Ctx

final class ClientResponseStreamHandler(val callback: HttpContent => Any)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  override def channelRead0(
    ctx: Ctx,
    msg: HttpContent,
  ): Unit = {
    val copiedMsg =
      if (!msg.isInstanceOf[LastHttpContent]) new DefaultHttpContent(Unpooled.copiedBuffer(msg.content())) else msg
    self.callback(copiedMsg)
    if (msg.isInstanceOf[LastHttpContent]) {
      ctx.channel().pipeline().remove(self)
    }
    msg.release(msg.refCnt()): Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }
}
