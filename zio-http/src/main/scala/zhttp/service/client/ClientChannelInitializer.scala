package zhttp.service.client

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}
import io.netty.handler.ssl.SslContext
import zhttp.core.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}

final case class ClientChannelInitializer[R](channelHandler: JChannelHandler, port: Int)
    extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    val sslCtx: Option[SslContext] = if (port == 443) Some(ClientSSLContext.getSSLContext()) else None
    val p: ChannelPipeline         = ch
      .pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)
    sslCtx match {
      case Some(value) => p.addFirst(value.newHandler(ch.alloc))
      case None        => ()
    }
    ()
  }
}
