package zhttp.service.server

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.HttpObjectDecoder.{
  DEFAULT_MAX_CHUNK_SIZE,
  DEFAULT_MAX_HEADER_SIZE,
  DEFAULT_MAX_INITIAL_LINE_LENGTH,
}
import io.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
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
            configureClearTextHttp2(reqHandler, http2ReqHandler, http2ResHandler, context.channel(), cfg)
            pipeline.remove(this)
            ()
          } else {
            pipeline.remove(this)
            ()
          }
        case _                       =>
          if (cfg.http2) {
            pipeline.remove(HTTP2_OR_HTTP_SERVER_HANDLER)

            context
              .channel()
              .pipeline()
              .replace(
                this,
                SERVER_DECODER_HANDLER,
                new HttpRequestDecoder(
                  DEFAULT_MAX_INITIAL_LINE_LENGTH,
                  DEFAULT_MAX_HEADER_SIZE,
                  DEFAULT_MAX_CHUNK_SIZE,
                  false,
                ),
              )
            context.channel().pipeline().addLast(SERVER_ENCODER_HANDLER, new HttpResponseEncoder())

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
