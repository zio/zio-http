package zhttp.service
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpContent
import zhttp.service.server.content.handlers.UnsafeRequestHandler.{UnsafeChannel, UnsafeContent}

@Sharable
final case class RequestBodyHandler[R](
  msgCallback: (UnsafeChannel, UnsafeContent) => Unit,
  config: Server.Config[R, Throwable],
) extends SimpleChannelInboundHandler[Any](false) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case httpContent: HttpContent =>
        msgCallback(UnsafeChannel(ctx), UnsafeContent(httpContent, config.maxRequestSize))
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
