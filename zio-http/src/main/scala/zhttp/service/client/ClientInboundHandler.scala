package zhttp.service.client

import io.netty.channel.ChannelHandlerContext
import zhttp.core.{JFullHttpResponse, JSimpleChannelInboundHandler}
import zhttp.service.UnsafeChannelExecutor

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
