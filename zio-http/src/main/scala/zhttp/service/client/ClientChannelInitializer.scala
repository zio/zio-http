package zhttp.service.client

import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.http.websocketx.{WebSocketClientProtocolConfig, WebSocketClientProtocolHandler}
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.content.handlers.ClientResponseHandler

final case class ClientChannelInitializer[R](
  channelHandler: ChannelHandler,
  scheme: String,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  config: Option[WebSocketClientProtocolConfig] = None,
) extends ChannelInitializer[Channel]() {
  override def initChannel(ch: Channel): Unit = {
    val p: ChannelPipeline = ch
      .pipeline()
      .addLast("HTTP_CLIENT_CODEC", new HttpClientCodec)
      .addLast("HTTP_OBJECT_AGGREGATOR", new HttpObjectAggregator(Int.MaxValue))
      .addLast("CLIENT_RESPONSE_HANDLER", new ClientResponseHandler())

    if (scheme == "ws") {
      config.map { c =>
        p.addAfter("HTTP_OBJECT_AGGREGATOR", "WEBSOCKET_CLIENT_PROTOCOL", new WebSocketClientProtocolHandler(c))
        p.addAfter("WEBSOCKET_CLIENT_PROTOCOL", "CLIENT_SOCKET_HANDLER", channelHandler)
      }

    } else {
      p.addLast(channelHandler)

      if (scheme == "https") {
        p.addFirst(ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc))
      }
    }
    ()
  }
}
