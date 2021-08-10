package zhttp.service.client

import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

final case class ClientChannelInitializer[R](
  channelHandler: ChannelHandler,
  scheme: String,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends ChannelInitializer[Channel]() {
  override def initChannel(ch: Channel): Unit = {
    val p: ChannelPipeline = ch
      .pipeline()
      .addLast(new HttpClientCodec)
      .addLast(new HttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)

    if (scheme == "https") {
      p.addFirst(ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc))
    }
    ()
  }
}
