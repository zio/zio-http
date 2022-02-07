package zhttp.service.client.content.handlers

import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer, ChannelPipeline}
//import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator}
import zhttp.service.{CLIENT_INBOUND_HANDLER, HTTP_CLIENT_CODEC, HTTP_OBJECT_AGGREGATOR, SSL_HANDLER}
import zhttp.service.client.ClientSSLHandler
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.model.ClientConnectionState.ReqKey
//import zhttp.socket.Socket

final case class NewClientChannelInitializer[R](
  channelHandler: ChannelHandler,
  isWebSocket: Boolean,
  isSSL: Boolean,
  reqKey: ReqKey,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends ChannelInitializer[Channel]() {

  override def initChannel(ch: Channel): Unit = {
    val pipeline: ChannelPipeline = ch.pipeline()

    if (isSSL) pipeline.addLast(SSL_HANDLER, ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc, reqKey.getHostName, reqKey.getPort))

    // Adding default client channel handlers
    pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec)

    // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
    // This is also required to make WebSocketHandlers work
    pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))

    pipeline.addLast(new NewClientResponseHandler())
    // ClientInboundHandler is used to take ClientResponse from FullHttpResponse
    pipeline.addLast(CLIENT_INBOUND_HANDLER, channelHandler): Unit

    // Add WebSocketHandlers if it's a `ws` or `wss` request
//    if (isWebSocket) {
//      val headers = req.getHeaders.encode
//      val app     = req.attribute.socketApp.getOrElse(Socket.empty.toSocketApp)
//      val config  = app.protocol.clientBuilder
//        .customHeaders(headers)
//        .webSocketUri(req.url.encode)
//        .build()
//
//      // Handles the heavy lifting required to upgrade the connection to a WebSocket connection
//      pipeline.addLast(WEB_SOCKET_CLIENT_PROTOCOL_HANDLER, new WebSocketClientProtocolHandler(config))
//      pipeline.addLast(WEB_SOCKET_HANDLER, new WebSocketAppHandler(rtm, app))
//    }
//    ()
  }
}
