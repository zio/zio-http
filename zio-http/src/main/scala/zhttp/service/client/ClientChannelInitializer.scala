package zhttp.service.client

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}
import io.netty.handler.ssl.SslContext
import zhttp.core.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}

final case class ClientChannelInitializer[R](channelHandler: JChannelHandler, port: Int)
    extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    import io.netty.handler.ssl.SslContextBuilder
    import io.netty.handler.ssl.util.InsecureTrustManagerFactory
    val sslCtx: SslContext =
      if (port == 443) SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
      else null
    val p: ChannelPipeline = ch
      .pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)
    if (sslCtx != null)
      p.addFirst(sslCtx.newHandler(ch.alloc))
    ()
  }
}
