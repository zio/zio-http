package zhttp.service.client

import io.netty.channel.{ChannelPipeline => JChannelPipeline}
import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}
import zhttp.core.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

final case class ClientChannelInitializer[R](
  channelHandler: JChannelHandler,
  scheme: String,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    val p: JChannelPipeline = ch
      .pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)

    if (scheme == "https") {
      p.addFirst(ClientSSLHandler.ssl(sslOption, false).newHandler(ch.alloc))
    }
    ()
  }
}
