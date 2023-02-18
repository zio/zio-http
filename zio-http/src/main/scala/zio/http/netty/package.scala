package zio.http

import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.util.AttributeKey
import zio._
import zio.http.model._

import scala.annotation.tailrec
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object netty {

  private[zio] val AutoReleaseRequest = false

  private[zio] object Names {
    val ServerCodecHandler             = "SERVER_CODEC"
    val HttpObjectAggregator           = "HTTP_OBJECT_AGGREGATOR"
    val HttpRequestHandler             = "HTTP_REQUEST"
    val HttpResponseHandler            = "HTTP_RESPONSE"
    val HttpKeepAliveHandler           = "HTTP_KEEPALIVE"
    val FlowControlHandler             = "FLOW_CONTROL_HANDLER"
    val WebSocketHandler               = "WEB_SOCKET_HANDLER"
    val SSLHandler                     = "SSL_HANDLER"
    val HttpOnHttpsHandler             = "HTTP_ON_HTTPS_HANDLER"
    val HttpServerCodec                = "HTTP_SERVER_CODEC"
    val HttpClientCodec                = "HTTP_CLIENT_CODEC"
    val HttpServerExpectContinue       = "HTTP_SERVER_EXPECT_CONTINUE"
    val HttpServerFlushConsolidation   = "HTTP_SERVER_FLUSH_CONSOLIDATION"
    val ClientInboundHandler           = "CLIENT_INBOUND_HANDLER"
    val WebSocketClientProtocolHandler = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
    val HttpRequestDecompression       = "HTTP_REQUEST_DECOMPRESSION"
    val HttpResponseCompression        = "HTTP_RESPONSE_COMPRESSION"
    val LowLevelLogging                = "LOW_LEVEL_LOGGING"
    val ProxyHandler                   = "PROXY_HANDLER"
    val HttpContentHandler             = "HTTP_CONTENT_HANDLER"
  }

}
