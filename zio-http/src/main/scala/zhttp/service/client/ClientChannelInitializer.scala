package zhttp.service.client

import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}
import zhttp.core.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}

final case class ClientChannelInitializer[R](channelHandler: JChannelHandler) extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    ch.pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)
    ()
  }
}
