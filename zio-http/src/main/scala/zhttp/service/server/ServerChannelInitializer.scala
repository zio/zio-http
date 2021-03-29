package zhttp.service.server

import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import io.netty.handler.ssl.SslContext
import zhttp.core._
import zhttp.service.{HTTP_KEEPALIVE_HANDLER, HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR, SERVER_CODEC_HANDLER, SSL}

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer(
  httpH: JChannelHandler,
  maxSize: Int,
  maybeSslContext: Option[SslContext] = None,
) extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {
    maybeSslContext
      .fold(channel.pipeline()) { sslContext =>
        channel
          .pipeline()
          .addLast(SSL, sslContext.newHandler(channel.alloc()))
      }
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(maxSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }
}
