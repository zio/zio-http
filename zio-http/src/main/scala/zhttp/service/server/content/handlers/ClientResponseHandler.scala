package zhttp.service.server.content.handlers

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zhttp.http.HTTP_CHARSET
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}

final class ClientResponseHandler(val callback: UnsafeChannel => UnsafeContent => Unit)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  private var onMessage: UnsafeContent => Unit = _

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    println("Client response handler: " + msg.content().toString(HTTP_CHARSET))
    self.onMessage(new UnsafeContent(msg))
    if (msg.isInstanceOf[LastHttpContent]) {
      ctx.channel().pipeline().remove(self): Unit
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    println("Client response handler: added")
    self.onMessage = callback(new UnsafeChannel(ctx))
    ctx.read(): Unit
  }

}
