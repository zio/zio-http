package zhttp

import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, ServerChannel}
import zio.Has

package object service {
  private[service] val AUTO_RELEASE_REQUEST                    = false
  private[service] val SERVER_CODEC_HANDLER                    = "SERVER_CODEC_HANDLER"
  private[service] val SERVER_DECODER_HANDLER                  = "SERVER_DECODER_HANDLER"
  private[service] val SERVER_ENCODER_HANDLER                  = "SERVER_ENCODER_HANDLER"
  private[service] val SERVER_OBJECT_AGGREGATOR_HANDLER        = "SERVER_OBJECT_AGGREGATOR_HANDLER"
  private[service] val HTTP_SERVER_REQUEST_HANDLER             = "HTTP_SERVER_REQUEST_HANDLER"
  private[service] val HTTP_SERVER_RESPONSE_HANDLER            = "HTTP_SERVER_RESPONSE_HANDLER"
  private[service] val HTTP_KEEPALIVE_HANDLER                  = "HTTP_KEEPALIVE_HANDLER"
  private[service] val FLOW_CONTROL_HANDLER                    = "FLOW_CONTROL_HANDLER"
  private[service] val WEB_SOCKET_HANDLER                      = "WEB_SOCKET_HANDLER"
  private[service] val SSL_HANDLER                             = "SSL_HANDLER"
  private[service] val HTTP_ON_HTTPS_HANDLER                   = "HTTP_ON_HTTPS_HANDLER"
  private[service] val HTTP_SERVER_EXPECT_CONTINUE_HANDLER     = "HTTP_SERVER_EXPECT_CONTINUE_HANDLER"
  private[service] val HTTP_SERVER_FLUSH_CONSOLIDATION_HANDLER = "HTTP_SERVER_FLUSH_CONSOLIDATION_HANDLER"
  private[service] val HTTP2_SERVER_CODEC_HANDLER              = "HTTP2_SERVER_CODEC_HANDLER"
  private[service] val HTTP2_REQUEST_HANDLER                   = "HTTP2_REQUEST_HANDLER"

  type ChannelFactory       = Has[JChannelFactory[Channel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[ServerChannel]]
  type UServer              = Server[Any, Nothing]
}
