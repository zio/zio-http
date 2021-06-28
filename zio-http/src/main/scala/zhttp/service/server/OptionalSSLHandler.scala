package zhttp.service.server

import io.netty.handler.codec.{ByteToMessageDecoder => JByteToMessageDecoder}
import io.netty.handler.ssl.{SslContext => JSslContext, SslHandler => JSslHandler}
import zhttp.core.{JByteBuf, JChannelHandler, JChannelHandlerContext, JHttpServerCodec}
import zhttp.service.Server.Settings
import zhttp.service._
import zhttp.service.server.ServerSSLHandler.SSLHttpBehaviour

import java.util

class OptionalSSLHandler[R](
  httpH: JChannelHandler,
  http2H: JChannelHandler,
  sslContext: JSslContext,
  settings: Settings[R, Throwable],
) extends JByteToMessageDecoder {
  override def decode(context: JChannelHandlerContext, in: JByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes < 5)
      ()
    else if (JSslHandler.isEncrypted(in)) {
      context.pipeline().replace(this, SSL_HANDLER, sslContext.newHandler(context.alloc()))
      ()
    } else {
      settings.sslOption.httpBehaviour match {
        case SSLHttpBehaviour.Accept =>
          context.channel().pipeline().remove(HTTP2_OR_HTTP_HANDLER)
          ServerChannelInitializer.configureClearText(httpH, http2H, context.channel(), settings)
          context.channel().pipeline().remove(this)
          ()
        case _                       =>
          context.channel().pipeline().remove(HTTP2_OR_HTTP_HANDLER)
          context.channel().pipeline().replace(this, SERVER_CODEC_HANDLER, new JHttpServerCodec)
          context
            .channel()
            .pipeline()
            .addLast(HTTP_ON_HTTPS_HANDLER, new HttpOnHttpsHandler(settings.sslOption.httpBehaviour))
          ()
      }
    }
  }
}
