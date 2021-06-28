package zhttp

import zhttp.core._
import zio.Has

package object service {
  private[service] val AUTO_RELEASE_REQUEST              = false
  private[service] val SERVER_CODEC_HANDLER              = "SERVER_CODEC"
  private[service] val HTTP2_SERVER_CODEC_HANDLER        = "HTTP2_SERVER_CODEC"
  private[service] val OBJECT_AGGREGATOR                 = "OBJECT_AGGREGATOR"
  private[service] val HTTP_REQUEST_HANDLER              = "HTTP_REQUEST"
  private[service] val HTTP2_REQUEST_HANDLER             = "HTTP2_REQUEST"
  private[service] val HTTP_KEEPALIVE_HANDLER            = "HTTP_KEEPALIVE"
  private[service] val WEB_SOCKET_HANDLER                = "WEB_SOCKET_HANDLER"
  private[service] val SSL_HANDLER                       = "SSL_HANDLER"
  private[service] val HTTP_ON_HTTPS_HANDLER             = "HTTP_ON_HTTPS_HANDLER"
  private[service] val CLEAR_TEXT_HTTP2_HANDLER          = "CLEAR_TEXT_HTTP2"
  private[service] val CLEAR_TEXT_HTTP2_FALLBACK_HANDLER = "CLEAR_TEXT_HTTP2_FALLBACK"
  private[service] val HTTP2_OR_HTTP_HANDLER             = "HTTP2_OR_HTTP"

  type ChannelFactory       = Has[JChannelFactory[JChannel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[JServerChannel]]
  type UServer              = Server[Any, Nothing]
}
