package zhttp.service.client.content.handlers

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http.{Headers, Status}
import zhttp.service.Client.ClientResponse

/**
 * Transforms a Netty FullHttpResponse into a zio-http specific ClientResponse.
 */
final class NewClientResponseHandler() extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val status   = Status.fromHttpResponseStatus(msg.status())
    val headers  = Headers.decode(msg.headers())
    val content  = Unpooled.copiedBuffer(msg.content())
    val response = ClientResponse(status, headers, content)
    ctx.fireChannelRead(response): Unit
  }
}
