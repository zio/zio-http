package zhttp.service.client

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}
import io.netty.handler.ssl.SslContext
import zhttp.core.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

final case class ClientChannelInitializer[R](
  channelHandler: JChannelHandler,
  scheme: String,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    val sslCtx: SslContext =
      if (scheme == "https") ClientSSLHandler.ssl(sslOption) else null
    val p: ChannelPipeline = ch
      .pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)
    if (sslCtx != null) p.addFirst(sslCtx.newHandler(ch.alloc))
    ()
  }
}
