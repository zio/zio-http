package zhttp.service.client

import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator}
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

final case class ClientChannelInitializer[R](
  handlers: List[ChannelHandler],
  scheme: String,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends ChannelInitializer[Channel]() {
  override def initChannel(ch: Channel): Unit = {
    val p: ChannelPipeline = ch
      .pipeline()
      .addLast(HTTP_CLIENT_CODEC, new HttpClientCodec)
      .addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))

    handlers.map(h => p.addLast(h))

    if (scheme == "https") {
      p.addFirst(ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc))
    }
    ()
  }
}
