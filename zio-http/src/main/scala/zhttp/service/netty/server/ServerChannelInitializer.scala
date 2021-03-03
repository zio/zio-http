package zhttp.service.netty.server

import zhttp.core.netty._
import zhttp.service.netty.{HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR, SERVER_CODEC_HANDLER}

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer(httpH: JChannelHandler) extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {
    channel
      .pipeline()
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(Int.MaxValue))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }
}
