package zhttp.service
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}

final class RequestBodyHandler(val callback: HttpContent => Any)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    self.callback(msg)
    if (msg.isInstanceOf[LastHttpContent]) {
      ctx.channel().pipeline().remove(self): Unit
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }
}
