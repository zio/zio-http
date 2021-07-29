package zhttp.service.client

import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer, ChannelPipeline => JChannelPipeline}
import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec, HttpObjectAggregator}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

final case class ClientChannelInitializer[R](
  channelHandler: ChannelHandler,
  scheme: String,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends ChannelInitializer[Channel]() {
  override def initChannel(ch: Channel): Unit = {
    val p: JChannelPipeline = ch
      .pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new HttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)

    if (scheme == "https") {
      p.addFirst(ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc))
    }
    ()
  }
}
