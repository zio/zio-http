package zhttp.service.server

import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import zhttp.core._
import zhttp.service.{HTTP_KEEPALIVE_HANDLER, HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR, SERVER_CODEC_HANDLER}

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer(httpH: JChannelHandler, maxSize: Int, enableSsl: Boolean = false)
    extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {
    val p = channel
      .pipeline()
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(maxSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)

    if (enableSsl)
      ServerSslHandler.ssl match {
        case Some(ssl) =>
          p.addFirst("ssl", ssl.newHandler(channel.alloc()))
          ()
        case None      => ()
      }
    else
      ()
  }
}
