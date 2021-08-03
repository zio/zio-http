package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.service.UnsafeChannelExecutor

/**
 * Handles HTTP response
 */
final case class ClientInboundHandler[R](
  zExec: UnsafeChannelExecutor[R],
  reader: ClientHttpChannelReader[Throwable, FullHttpResponse],
) extends SimpleChannelInboundHandler[FullHttpResponse](false) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit =
    zExec.unsafeExecute_(ctx)(reader.onChannelRead(msg))

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit =
    zExec.unsafeExecute_(ctx)(reader.onExceptionCaught(error))

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    zExec.unsafeExecute_(ctx)(reader.onActive(ctx))
}
