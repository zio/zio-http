package zhttp.service.server

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpMessage

class RedirectHttpsHandler() extends SimpleChannelInboundHandler[HttpMessage] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage): Unit = {
    import io.netty.channel.ChannelFutureListener
    import io.netty.handler.codec.http.{DefaultHttpResponse, HttpMessage, HttpResponseStatus, HttpVersion}
    if (msg.isInstanceOf[HttpMessage]) {
      val message  = msg.asInstanceOf[HttpMessage]
      val address  = message.headers.get("Host")
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PERMANENT_REDIRECT)
      if (address != null) {
        response.headers.set("Location", "https://" + address)
      }
      ctx.channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
      ()
    }
  }

}
