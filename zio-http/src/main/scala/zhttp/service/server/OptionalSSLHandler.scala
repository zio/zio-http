package zhttp.service.server

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.{SslContext, SslHandler}
import zhttp.service._
import zhttp.service.server.ServerSSLHandler.SSLHttpBehaviour

import java.util

class OptionalSSLHandler(sslContext: SslContext, httpBehaviour: SSLHttpBehaviour, cfg: Server.Config[_, _])
    extends ByteToMessageDecoder {
  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    val pipeline = context.channel().pipeline()
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in)) {
      pipeline.replace(this, SSL_HANDLER, sslContext.newHandler(context.alloc()))
      ()
    } else {
      httpBehaviour match {
        case SSLHttpBehaviour.Accept =>
          pipeline.remove(this)
          ()
        case _                       =>
          pipeline.remove(HTTP_SERVER_REQUEST_HANDLER)
          if (cfg.keepAlive) pipeline.remove(HTTP_KEEPALIVE_HANDLER)
          pipeline.remove(this)
          pipeline.addLast(new HttpOnHttpsHandler(httpBehaviour))
          ()
      }
    }
  }
}
