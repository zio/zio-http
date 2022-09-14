package zio.http

package object netty {

  private[zio] val AutoReleaseRequest = false

  object Names {
    private[zio] val ServerCodecHandler             = "SERVER_CODEC"
    private[zio] val HttpObjectAggregator           = "HTTP_OBJECT_AGGREGATOR"
    private[zio] val HttpRequestHandler             = "HTTP_REQUEST"
    private[zio] val HttpResponseHandler            = "HTTP_RESPONSE"
    private[zio] val HttpKeepAliveHandler           = "HTTP_KEEPALIVE"
    private[zio] val FlowControlHandler             = "FLOW_CONTROL_HANDLER"
    private[zio] val WebSocketHandler               = "WEB_SOCKET_HANDLER"
    private[zio] val SSLHandler                     = "SSL_HANDLER"
    private[zio] val HttpOnHttpsHandler             = "HTTP_ON_HTTPS_HANDLER"
    private[zio] val HttpServerCodec                = "HTTP_SERVER_CODEC"
    private[zio] val HttpClientCodec                = "HTTP_CLIENT_CODEC"
    private[zio] val HttpServerExpectContinue       = "HTTP_SERVER_EXPECT_CONTINUE"
    private[zio] val HttpServerFlushConsolidation   = "HTTP_SERVER_FLUSH_CONSOLIDATION"
    private[zio] val ClientInboundHandler           = "CLIENT_INBOUND_HANDLER"
    private[zio] val WebSocketClientProtocolHandler = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
    private[zio] val HttpRequestDecompression       = "HTTP_REQUEST_DECOMPRESSION"
    private[zio] val LowLevelLogging                = "LOW_LEVEL_LOGGING"
    private[zio] val ProxyHandler                   = "PROXY_HANDLER"
    private[zio] val HttpContentHandler             = "HTTP_CONTENT_HANDLER"
  }

}
