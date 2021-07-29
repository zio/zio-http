package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec, HttpServerKeepAliveHandler}
import zhttp.service.Server.Settings
import zhttp.service.{
  HTTP_KEEPALIVE_HANDLER,
  HTTP_REQUEST_HANDLER,
  OBJECT_AGGREGATOR,
  SERVER_CODEC_HANDLER,
  SSL_HANDLER,
}

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](httpH: ChannelHandler, settings: Settings[R, Throwable])
    extends ChannelInitializer[Channel] {
  override def initChannel(channel: Channel): Unit = {

    val sslctx = if (settings.sslOption == null) null else settings.sslOption.sslContext
    if (sslctx != null) {
      channel
        .pipeline()
        .addFirst(
          SSL_HANDLER,
          new OptionalSSLHandler(sslctx, settings.sslOption.httpBehaviour),
        )
      ()
    }
    channel
      .pipeline()
      .addLast(SERVER_CODEC_HANDLER, new HttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(settings.maxRequestSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }

}
