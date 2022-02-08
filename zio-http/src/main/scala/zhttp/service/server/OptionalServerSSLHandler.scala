package zhttp.service.server

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.{SslContext, SslHandler}
import zhttp.service.Server.Config
import zhttp.service._
import zhttp.service.server.ServerChannelInitializerUtil.configureClearTextHttp2
import zhttp.service.server.ServerSSLBuilder.SSLHttpBehaviour

import java.util

class OptionalServerSSLHandler(
  sslContext: SslContext,
  cfg: Config[_, Throwable],
  reqHandler: ChannelHandler,
  respHandler: ChannelHandler,
  http2ReqHandler: ChannelHandler,
  http2ResHandler: ChannelHandler,
) extends ByteToMessageDecoder {
  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    val pipeline = context.channel().pipeline()
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in)) {
      pipeline.replace(this, SERVER_SSL_HANDLER, sslContext.newHandler(context.alloc()))
      ()
    } else {
      cfg.sslOption.httpBehaviour match {
        case SSLHttpBehaviour.Accept =>
          if (cfg.http2) {
            pipeline.remove(HTTP2_OR_HTTP_SERVER_HANDLER)
            configureClearTextHttp2(reqHandler, respHandler, http2ReqHandler, http2ResHandler, context.channel(), cfg)
            pipeline.remove(this)
            ()
          } else {
            pipeline.remove(this)
            ()
          }
        case _                       =>
          if (cfg.http2) {
            pipeline.remove(HTTP2_OR_HTTP_SERVER_HANDLER)
            context.channel().pipeline().replace(this, SERVER_CODEC_HANDLER, new HttpServerCodec)
            context
              .channel()
              .pipeline()
              .addLast(HTTP_ON_HTTPS_HANDLER, new HttpOnHttpsHandler(cfg.sslOption.httpBehaviour))
            ()
          } else {
            pipeline.replace(
              HTTP_SERVER_REQUEST_HANDLER,
              HTTP_ON_HTTPS_HANDLER,
              new HttpOnHttpsHandler(cfg.sslOption.httpBehaviour),
            )
            pipeline.remove(this)
            ()
          }

      }
    }
  }
}
