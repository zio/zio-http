package zio.http

package object netty {

  private[zio] object Names {
    val HttpObjectAggregator           = "HTTP_OBJECT_AGGREGATOR"
    val HttpRequestHandler             = "HTTP_REQUEST"
    val HttpKeepAliveHandler           = "HTTP_KEEPALIVE"
    val FlowControlHandler             = "FLOW_CONTROL_HANDLER"
    val WebSocketHandler               = "WEB_SOCKET_HANDLER"
    val SSLHandler                     = "SSL_HANDLER"
    val HttpClientCodec                = "HTTP_CLIENT_CODEC"
    val HttpServerExpectContinue       = "HTTP_SERVER_EXPECT_CONTINUE"
    val HttpServerFlushConsolidation   = "HTTP_SERVER_FLUSH_CONSOLIDATION"
    val ClientInboundHandler           = "CLIENT_INBOUND_HANDLER"
    val ClientStreamingBodyHandler     = "CLIENT_STREAMING_BODY_HANDLER"
    val WebSocketClientProtocolHandler = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
    val HttpRequestDecompression       = "HTTP_REQUEST_DECOMPRESSION"
    val HttpResponseCompression        = "HTTP_RESPONSE_COMPRESSION"
    val LowLevelLogging                = "LOW_LEVEL_LOGGING"
    val HttpContentHandler             = "HTTP_CONTENT_HANDLER"
    val HttpRequestDecoder             = "HTTP_REQUEST_DECODER"
    val HttpResponseEncoder            = "HTTP_RESPONSE_ENCODER"
    val ProxyHandler                   = "PROXY_HANDLER"
  }
}
