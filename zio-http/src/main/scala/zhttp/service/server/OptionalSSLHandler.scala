package zhttp.service.server

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.{SslContext, SslHandler}
import zhttp.service._
import zhttp.service.server.ServerSSLHandler.SSLHttpBehaviour

import java.util

class OptionalSSLHandler(sslContext: SslContext, httpBehaviour: SSLHttpBehaviour) extends ByteToMessageDecoder {
  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in)) {
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
