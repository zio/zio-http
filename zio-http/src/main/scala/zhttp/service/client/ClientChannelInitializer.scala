package zhttp.service.client

import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}
import io.netty.handler.ssl.SslContext
import zhttp.core.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}

final case class ClientChannelInitializer[R](
  channelHandler: JChannelHandler,
  maybeSslContext: Option[SslContext] = None,
) extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    maybeSslContext
      .fold(ch.pipeline()) { sslContext =>
        ch.pipeline().addLast(sslContext.newHandler(ch.alloc()))
      }
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)

    ()
  }
}
