package zio.http
package netty
package server

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.{SslContext, SslHandler}
import zio.http.netty.server.ServerSSLHandler.SSLHttpBehaviour

import java.util

private[zio] class ServerSSLDecoder(sslContext: SslContext, httpBehaviour: SSLHttpBehaviour, cfg: ServerConfig)
    extends ByteToMessageDecoder {
  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    val pipeline = context.channel().pipeline()
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in)) {
      pipeline.replace(this, service.SSL_HANDLER, sslContext.newHandler(context.alloc()))
      ()
    } else {
      httpBehaviour match {
        case SSLHttpBehaviour.Accept =>
          pipeline.remove(this)
          ()
        case _                       =>
          pipeline.remove(service.HTTP_REQUEST_HANDLER)
          if (cfg.keepAlive) pipeline.remove(service.HTTP_KEEPALIVE_HANDLER)
          pipeline.remove(this)
          pipeline.addLast(new ServerHttpsHandler(httpBehaviour))
          ()
      }
    }
  }
}
