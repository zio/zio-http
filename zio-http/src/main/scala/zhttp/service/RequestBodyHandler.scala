package zhttp.service
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}

final class RequestBodyHandler[R](val callback: UnsafeChannel => UnsafeContent => Unit)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  private var onMessage: UnsafeContent => Unit = _

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    self.onMessage(new UnsafeContent(msg))
    if (msg.isInstanceOf[LastHttpContent]) {
      ctx.channel().pipeline().remove(self): Unit
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    self.onMessage = callback(new UnsafeChannel(ctx))
    ctx.channel().config().setAutoRead(false)
    ctx.read(): Unit
  }
}
