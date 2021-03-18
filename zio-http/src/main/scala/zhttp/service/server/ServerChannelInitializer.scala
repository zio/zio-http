package zhttp.service.server

import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import zhttp.core._
import zhttp.service.{HTTP_KEEPALIVE_HANDLER, HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR, SERVER_CODEC_HANDLER}

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer(httpH: JChannelHandler) extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {
    channel
      .pipeline()
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(Int.MaxValue))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }
}
