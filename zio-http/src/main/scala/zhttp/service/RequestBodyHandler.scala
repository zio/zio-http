package zhttp.service
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpContent
import zhttp.http.{Response, Status}
import zhttp.service.server.content.handlers.UnsafeRequestHandler.{UnsafeChannel, UnsafeContent}

@Sharable
final case class RequestBodyHandler[R](
  msgCallback: (UnsafeChannel, UnsafeContent) => Unit,
  config: Server.Config[R, Throwable],
) extends SimpleChannelInboundHandler[Any](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: Any): Unit = {

    println("RBODY: " + msg)
    if (msg.isInstanceOf[HttpContent]) {
      val httpContent = msg.asInstanceOf[HttpContent]
      if (httpContent.content().readableBytes() > config.maxRequestSize)
        ctx.fireChannelRead(Response.status(Status.REQUEST_ENTITY_TOO_LARGE)): Unit
      else
        msgCallback(UnsafeChannel(ctx), UnsafeContent(msg.asInstanceOf[HttpContent]))
    } else
      ctx.fireChannelRead(msg): Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setAutoRead(false)
    ctx.read(): Unit
  }
}
