package zhttp

import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, ServerChannel}
import zio.Has

package object service {
  private[service] val AUTO_RELEASE_REQUEST              = false
  private[service] val SERVER_CODEC_HANDLER              = "SERVER_CODEC"
  private[service] val CLIENT_CODEC_HANDLER              = "CLIENT_CODEC"
  private[service] val OBJECT_AGGREGATOR                 = "OBJECT_AGGREGATOR"
  private[service] val HTTP_REQUEST_HANDLER              = "HTTP_REQUEST"
  private[service] val HTTP_KEEPALIVE_HANDLER            = "HTTP_KEEPALIVE"
  private[service] val WEB_SOCKET_HANDLER                = "WEB_SOCKET_HANDLER"
  private[service] val SSL_HANDLER                       = "SSL_HANDLER"
  private[service] val HTTP_ON_HTTPS_HANDLER             = "HTTP_ON_HTTPS_HANDLER"
  private[service] val HTTP2_SERVER_CODEC_HANDLER        = "HTTP2_SERVER_CODEC"
  private[service] val HTTP2_REQUEST_HANDLER             = "HTTP2_REQUEST"
  private[service] val SERVER_CLEAR_TEXT_HTTP2_HANDLER          = "SERVER_CLEAR_TEXT_HTTP2"
  private[service] val SERVER_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER = "SERVER_CLEAR_TEXT_HTTP2_FALLBACK"
  private[service] val HTTP2_OR_HTTP_SERVER_HANDLER             = "HTTP2_OR_HTTP_SERVER_HANDLER"
  private[service] val ENCRYPTION_FILTER_HANDLER         = "ENCRYPTION_FILTER"
  private[service] val HTTP_RESPONSE_HANDLER               = "HTTP_RESPONSE_HANDLER"
  private[service] val HTTP2_CONNECTION_HANDLER="HTTP2_CONNECTION_HANDLER"
  private[service] val HTTP2_SETTINGS_HANDLER ="HTTP2_SETTINGS_HANDLER"
  private[service] val HTTP2_RESPONSE_HANDLER               = "HTTP2_RESPONSE_HANDLER"
  private[service] val CLIENT_CLEAR_TEXT_HTTP2_HANDLER          = "CLIENT_CLEAR_TEXT_HTTP2"
  private[service] val CLIENT_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER = "CLIENT_CLEAR_TEXT_HTTP2_FALLBACK"
  private[service] val CLIENT_UPGRADE_REQUEST_HANDLER="CLIENT_UPGRADE_REQUEST_HANDLER"
  private[service] val HTTP2_OR_HTTP_CLIENT_HANDLER             = "HTTP2_OR_HTTP_CLIENT_HANDLER"

  type ChannelFactory       = Has[JChannelFactory[Channel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[ServerChannel]]
  type UServer              = Server[Any, Nothing]
}
