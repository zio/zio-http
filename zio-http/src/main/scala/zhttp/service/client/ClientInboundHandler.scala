package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.service.Client.ClientResponse
import zhttp.service.{DecodeJResponse, HttpRuntime}

/**
 * Handles HTTP response
 */
final case class ClientInboundHandler[R](
  zExec: HttpRuntime[R],
  reader: ClientHttpChannelReader[Throwable, ClientResponse],
) extends SimpleChannelInboundHandler[FullHttpResponse](false)
    with DecodeJResponse {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val clientResponse = decodeJResponse(msg)
    zExec.unsafeRun(ctx)(reader.onChannelRead(clientResponse))
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit =
    zExec.unsafeRun(ctx)(reader.onExceptionCaught(error))

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    zExec.unsafeRun(ctx)(reader.onActive(ctx))
}
