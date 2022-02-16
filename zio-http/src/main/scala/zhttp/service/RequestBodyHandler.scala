package zhttp.service
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpContent
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}

@Sharable
final class RequestBodyHandler[R](
  var callback: UnsafeChannel => UnsafeContent => Unit,
) extends SimpleChannelInboundHandler[Any](false) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case httpContent: HttpContent =>
        callback(UnsafeChannel(ctx))(UnsafeContent(httpContent))
      case _                        =>
        ctx.fireChannelRead(msg): Unit
        ctx.read(): Unit
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setAutoRead(false)
    ctx.read(): Unit
  }
}
