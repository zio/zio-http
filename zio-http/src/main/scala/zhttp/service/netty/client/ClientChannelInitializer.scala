package zhttp.service.netty.client

import zhttp.core.netty.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}
import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}

final case class ClientChannelInitializer[R](channelHandler: JChannelHandler) extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    ch.pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)
    ()
  }
}
