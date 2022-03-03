package zhttp

import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, ServerChannel}
import zio.Has

package object service {
  private[service] val HTTP_ON_HTTPS_HANDLER                    = "HTTP_ON_HTTPS_HANDLER"
  private[service] val SERVER_SSL_HANDLER                       = "SERVER_SSL_HANDLER"
  private[service] val SERVER_DECODER_HANDLER                   = "SERVER_DECODER_HANDLER"
  private[service] val SERVER_ENCODER_HANDLER                   = "SERVER_ENCODER_HANDLER"
  private[service] val SERVER_CODEC_HANDLER                     = "SERVER_CODEC_HANDLER"
  private[service] val SERVER_OBJECT_AGGREGATOR                 = "SERVER_OBJECT_AGGREGATOR"
  private[service] val HTTP_SERVER_REQUEST_DECOMPRESSION        = "HTTP_SERVER_REQUEST_DECOMPRESSION"
  private[service] val HTTP_SERVER_OBJECT_AGGREGATOR            = "HTTP_SERVER_OBJECT_AGGREGATOR"
  private[service] val HTTP_SERVER_KEEPALIVE_HANDLER            = "HTTP_SERVER_KEEPALIVE_HANDLER"
  private[service] val SERVER_FLOW_CONTROL_HANDLER              = "SERVER_FLOW_CONTROL_HANDLER"
  private[service] val HTTP_SERVER_REQUEST_HANDLER              = "HTTP_SERVER_REQUEST_HANDLER"
  private[zhttp] val HTTP_CONTENT_HANDLER                       = "HTTP_CONTENT_HANDLER"
  private[service] val WEB_SOCKET_CLIENT_PROTOCOL_HANDLER       = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
  private[service] val SERVER_WEB_SOCKET_HANDLER                = "SERVER_WEB_SOCKET_HANDLER"
  private[service] val WEB_SOCKET_SERVER_PROTOCOL_HANDLER       = "WEB_SOCKET_SERVER_PROTOCOL_HANDLER"
  private[service] val CLIENT_WEB_SOCKET_HANDLER                = "CLIENT_WEB_SOCKET_HANDLER"
  private[service] val HTTP_SERVER_EXPECT_CONTINUE              = "HTTP_SERVER_EXPECT_CONTINUE"
  private[service] val HTTP_SERVER_FLUSH_CONSOLIDATION_HANDLER  = "HTTP_SERVER_FLUSH_CONSOLIDATION_HANDLER"
  private[service] val ENCRYPTION_FILTER_HANDLER                = "ENCRYPTION_FILTER_HANDLER"
  private[service] val HTTP2_OR_HTTP_SERVER_HANDLER             = "HTTP2_OR_HTTP_SERVER_HANDLER"
  private[service] val HTTP2_SERVER_CODEC_HANDLER               = "HTTP2_SERVER_CODEC_HANDLER"
  private[service] val HTTP2_SERVER_REQUEST_HANDLER             = "HTTP2_SERVER_REQUEST_HANDLER"
  private[service] val HTTP2_SERVER_RESPONSE_HANDLER            = "HTTP2_SERVER_RESPONSE_HANDLER"
  private[service] val SERVER_CLEAR_TEXT_HTTP2_HANDLER          = "SERVER_CLEAR_TEXT_HTTP2_HANDLER"
  private[service] val SERVER_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER = "SERVER_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER"
  private[service] val CLIENT_INBOUND_HANDLER                   = "CLIENT_INBOUND_HANDLER"
  private[service] val CLIENT_OBJECT_AGGREGATOR                 = "CLIENT_OBJECT_AGGREGATOR"
  private[service] val CLIENT_SSL_HANDLER                       = "CLIENT_SSL_HANDLER"
  private[service] val CLIENT_CODEC_HANDLER                     = "CLIENT_CODEC_HANDLER"
  type ChannelFactory       = Has[JChannelFactory[Channel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[ServerChannel]]
  type UServer              = Server[Any, Nothing]
}
