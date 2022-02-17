package zhttp.service
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpContent
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}

@Sharable
final class RequestBodyHandler[R](
  var callback: (UnsafeChannel, Int) => UnsafeContent => Unit,
  config: Server.Config[R, Throwable],
) extends SimpleChannelInboundHandler[HttpContent](false) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    callback(UnsafeChannel(ctx), config.maxRequestSize)(UnsafeContent(msg))
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setAutoRead(false)
    ctx.read(): Unit
  }
}
