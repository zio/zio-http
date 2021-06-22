package zhttp.service.server

import io.netty.handler.codec.{ByteToMessageDecoder => JByteToMessageDecoder}
import io.netty.handler.ssl.{SslContext => JSslContext, SslHandler => JSslHandler}
import zhttp.core.{JByteBuf, JChannelHandlerContext}
import zhttp.service._
import zhttp.service.server.ServerSSLHandler.SSLHttpBehaviour

import java.util

class OptionalSSLHandler(sslContext: JSslContext, httpBehaviour: SSLHttpBehaviour) extends JByteToMessageDecoder {
  override def decode(context: JChannelHandlerContext, in: JByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes < 5)
      ()
    else if (JSslHandler.isEncrypted(in)) {
      context.pipeline().replace(this, SSL_HANDLER, sslContext.newHandler(context.alloc()))
      ()
    } else {
      httpBehaviour match {
        case SSLHttpBehaviour.Accept =>
          context.channel().pipeline().remove(this)
          ()
        case _                       =>
          context.channel().pipeline().remove(HTTP_REQUEST_HANDLER)
          context.channel().pipeline().remove(OBJECT_AGGREGATOR)
          context.channel().pipeline().remove(HTTP_KEEPALIVE_HANDLER)
          context.channel().pipeline().remove(this)
          context.channel().pipeline().addLast(new HttpOnHttpsHandler(httpBehaviour))
          ()
      }
    }
  }
}
