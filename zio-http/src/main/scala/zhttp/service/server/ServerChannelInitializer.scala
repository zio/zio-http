package zhttp.service.server

import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import io.netty.handler.ssl.{OptionalSslHandler, SslHandler}
import zhttp.core._
import zhttp.service.Server.Settings
import zhttp.service.server.ServerSSLHandler.{SSLHttpBehaviour, ServerSSLOptions}
import zhttp.service.{HTTP_KEEPALIVE_HANDLER, HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR, SERVER_CODEC_HANDLER}

import java.util

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer[R](httpH: JChannelHandler, settings: Settings[R, Throwable])
    extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {

    val sslctx = settings.sslOption match {
      case ServerSSLOptions.NoSSL                 => null
      case ServerSSLOptions.CustomSSL(sslContext) => sslContext
    }
    if (sslctx != null) {
      channel
        .pipeline()
        .addFirst(
          "ssl",
          new OptionalSslHandler(sslctx) {
            override def decode(context: JChannelHandlerContext, in: JByteBuf, out: util.List[AnyRef]): Unit = {
              if (in.readableBytes < 5)
                ()
              else if (SslHandler.isEncrypted(in)) {
                context.pipeline().replace("ssl", "ssl", sslctx.newHandler(context.alloc()))
                context
                  .pipeline()
                  .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
                  .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
                  .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
                  .addLast(HTTP_REQUEST_HANDLER, httpH)
                ()
              } else {
                settings.sslHttpBehaviour match {
                  case SSLHttpBehaviour.Redirect => {
                    context.pipeline().replace("ssl", SERVER_CODEC_HANDLER, new JHttpServerCodec)
                    context
                      .pipeline()
                      .addLast("RedirectHttpHandler", new RedirectHttpsHandler())
                    ()
                  }
                  case SSLHttpBehaviour.Accept   => {
                    context.pipeline().replace("ssl", SERVER_CODEC_HANDLER, new JHttpServerCodec)
                    context
                      .pipeline()
                      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
                      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
                      .addLast(HTTP_REQUEST_HANDLER, httpH)
                    ()
                  }
                }

              }

            }

          },
        )
      ()
    } else {
      channel
        .pipeline()
        .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
        .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
        .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
        .addLast(HTTP_REQUEST_HANDLER, httpH)
      ()
    }

  }

}
