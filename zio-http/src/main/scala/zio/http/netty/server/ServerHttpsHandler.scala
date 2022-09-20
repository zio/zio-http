package zio.http.netty.server

import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpResponse, HttpMessage, HttpResponseStatus, HttpVersion}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

import zio.http.SSLConfig.HttpBehaviour

private[zio] class ServerHttpsHandler(httpBehaviour: HttpBehaviour) extends SimpleChannelInboundHandler[HttpMessage] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage): Unit = {

    // TODO: PatMat maybe???
    if (msg.isInstanceOf[HttpMessage]) {
      if (httpBehaviour == HttpBehaviour.Redirect) {
        val message  = msg.asInstanceOf[HttpMessage]
        val address  = message.headers.get("Host")
        val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PERMANENT_REDIRECT)
        if (address != null) {
          response.headers.set("Location", "https://" + address)
        }
        ctx.channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
        ()
      } else {
        val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_ACCEPTABLE)
        ctx.channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
        ()
      }
    }
  }
}
