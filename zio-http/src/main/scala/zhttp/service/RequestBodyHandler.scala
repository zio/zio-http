package zhttp.service
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpContent
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}

import scala.annotation.unused

@Sharable
final class RequestBodyHandler[R](
  var callback: UnsafeChannel => UnsafeContent => Unit,
  @unused config: Server.Config[R, Throwable],
) extends SimpleChannelInboundHandler[HttpContent](false) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    callback(UnsafeChannel(ctx))(UnsafeContent(msg))
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setAutoRead(false)
    ctx.read(): Unit
  }
}
