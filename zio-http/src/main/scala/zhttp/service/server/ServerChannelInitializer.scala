package zhttp.service.server

import io.netty.handler.codec.http.{HttpServerKeepAliveHandler => JHttpServerKeepAliveHandler}
import zhttp.core._
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
@JSharable
final case class ServerChannelInitializer[R](httpH: JChannelHandler, settings: Settings[R, Throwable])
    extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {

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
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }

}
