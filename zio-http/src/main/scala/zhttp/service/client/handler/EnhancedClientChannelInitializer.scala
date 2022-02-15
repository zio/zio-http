package zhttp.service.client.handler

import io.netty.channel.{Channel, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator}
import zhttp.service.client.ClientSSLHandler
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.model.ConnectionData.ReqKey
import zhttp.service.{HTTP_CLIENT_CODEC, HTTP_OBJECT_AGGREGATOR, SSL_HANDLER}

final case class EnhancedClientChannelInitializer[R](
  isWebSocket: Boolean,
  isSSL: Boolean,
  reqKey: ReqKey,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends ChannelInitializer[Channel]() {

  override def initChannel(ch: Channel): Unit = {
    val pipeline: ChannelPipeline = ch.pipeline()

    if (isSSL)
      pipeline.addLast(
        SSL_HANDLER,
        ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc, reqKey.getHostName, reqKey.getPort),
      )

    // Adding default client channel handlers
    pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec)

    // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
    // This is also required to make WebSocketHandlers work
    pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue)): Unit

    // TODO: Handle Websocket
  }
}
