package zhttp.service.server

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.{SslContext, SslHandler}
import zhttp.service.Server.Settings
import zhttp.service._
import zhttp.service.server.ServerChannelInitializerUtil.configureClearText
import zhttp.service.server.ServerSSLHandler.SSLHttpBehaviour

import java.util

class OptionalSSLHandler[R](
  httpH: ChannelHandler,
  http2H: ChannelHandler,
  sslContext: SslContext,
  settings: Settings[R, Throwable],
) extends ByteToMessageDecoder {
  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in)) {
      context.pipeline().replace(this, SSL_HANDLER, sslContext.newHandler(context.alloc()))
      ()
    } else {
      settings.sslOption.httpBehaviour match {
        case SSLHttpBehaviour.Accept =>
          context.channel().pipeline().remove(HTTP2_OR_HTTP_HANDLER)
          configureClearText(httpH, http2H, context.channel(), settings)
          context.channel().pipeline().remove(this)
          ()
        case _                       =>
          context.channel().pipeline().remove(HTTP2_OR_HTTP_HANDLER)
          context.channel().pipeline().replace(this, SERVER_CODEC_HANDLER, new HttpServerCodec)
          context
            .channel()
            .pipeline()
            .addLast(HTTP_ON_HTTPS_HANDLER, new HttpOnHttpsHandler(settings.sslOption.httpBehaviour))
          ()
      }
    }
  }
}
