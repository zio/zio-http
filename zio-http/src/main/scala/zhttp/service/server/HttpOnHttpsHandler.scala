package zhttp.service.server

import io.netty.channel.{ChannelFutureListener => JChannelFutureListener}
import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpMessage => JHttpMessage,
  HttpResponseStatus => JHttpResponseStatus,
  HttpVersion => JHttpVersion,
}
import zhttp.core.{JChannelHandlerContext, JSimpleChannelInboundHandler}
import zhttp.service.server.ServerSSLHandler.SSLHttpBehaviour

class HttpOnHttpsHandler(httpBehaviour: SSLHttpBehaviour) extends JSimpleChannelInboundHandler[JHttpMessage] {
  override def channelRead0(ctx: JChannelHandlerContext, msg: JHttpMessage): Unit = {

    if (msg.isInstanceOf[JHttpMessage]) {
      if (httpBehaviour == SSLHttpBehaviour.Redirect) {
        val message  = msg.asInstanceOf[JHttpMessage]
        val address  = message.headers.get("Host")
        val response = new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, JHttpResponseStatus.PERMANENT_REDIRECT)
        if (address != null) {
          response.headers.set("Location", "https://" + address)
        }
        ctx.channel.writeAndFlush(response).addListener(JChannelFutureListener.CLOSE)
        ()
      } else {
        val response = new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, JHttpResponseStatus.NOT_ACCEPTABLE)
        ctx.channel.writeAndFlush(response).addListener(JChannelFutureListener.CLOSE)
        ()
      }
    }
  }
}
