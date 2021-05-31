package zhttp.service.server

import zhttp.core.{Direction, HBuf1}
import zhttp.http.HttpApp
import zhttp.service.UnsafeChannelExecutor
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler => JSimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest => JFullHttpRequest}

private[zhttp] final case class FullRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  cb: HBuf1[Direction.In] => HttpApp[R, Throwable],
) extends JSimpleChannelInboundHandler[JFullHttpRequest](false) {

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel.config.setAutoRead(true)
    ()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JFullHttpRequest): Unit = {
    ???
  }
}
