package zhttp.service.client.experimental

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http.{Headers, Status}

/**
 * Transforms a Netty FullHttpResponse into a zio-http specific ClientResponse.
 */
final class ZClientResponseHandler() extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val status   = Status.fromHttpResponseStatus(msg.status())
    val headers  = Headers.decode(msg.headers())
    val content  = Unpooled.copiedBuffer(msg.content())
    val response = Resp(status, headers, content)
    ctx.fireChannelRead(response)
    ()
  }
}
