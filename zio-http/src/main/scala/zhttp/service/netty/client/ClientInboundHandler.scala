package zhttp.service.netty.client

import io.netty.channel.ChannelHandlerContext
import zhttp.core.netty.{JFullHttpResponse, JSimpleChannelInboundHandler}
import zhttp.service.netty.UnsafeChannelExecutor

/**
 * Handles HTTP response
 */
final case class ClientInboundHandler[R](
  zExec: UnsafeChannelExecutor[R],
  reader: ClientHttpChannelReader[Throwable, JFullHttpResponse],
) extends JSimpleChannelInboundHandler[JFullHttpResponse](false) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: JFullHttpResponse): Unit =
    zExec.unsafeExecute_(ctx)(reader.onChannelRead(msg))

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit =
    zExec.unsafeExecute_(ctx)(reader.onExceptionCaught(error))

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    zExec.unsafeExecute_(ctx)(reader.onActive(ctx))
}
