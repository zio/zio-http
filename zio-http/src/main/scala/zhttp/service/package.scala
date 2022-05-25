package zhttp

import io.netty.channel.{
  Channel,
  ChannelFactory => JChannelFactory,
  ChannelHandlerContext,
  EventLoopGroup => JEventLoopGroup,
  ServerChannel,
}
import zio.Has

package object service extends Logging {
  type ChannelFactory       = Has[JChannelFactory[Channel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[ServerChannel]]
  type UServer              = Server[Any, Nothing]
  private[zhttp] type Ctx   = ChannelHandlerContext
  private[service] val AUTO_RELEASE_REQUEST               = false
  private[service] val SERVER_CODEC_HANDLER               = "SERVER_CODEC"
  private[service] val HTTP_OBJECT_AGGREGATOR             = "HTTP_OBJECT_AGGREGATOR"
  private[service] val HTTP_REQUEST_HANDLER               = "HTTP_REQUEST"
  private[service] val HTTP_RESPONSE_HANDLER              = "HTTP_RESPONSE"
  private[service] val HTTP_KEEPALIVE_HANDLER             = "HTTP_KEEPALIVE"
  private[service] val FLOW_CONTROL_HANDLER               = "FLOW_CONTROL_HANDLER"
  private[service] val WEB_SOCKET_HANDLER                 = "WEB_SOCKET_HANDLER"
  private[service] val SSL_HANDLER                        = "SSL_HANDLER"
  private[service] val HTTP_ON_HTTPS_HANDLER              = "HTTP_ON_HTTPS_HANDLER"
  private[service] val HTTP_SERVER_CODEC                  = "HTTP_SERVER_CODEC"
  private[service] val HTTP_CLIENT_CODEC                  = "HTTP_CLIENT_CODEC"
  private[service] val HTTP_SERVER_EXPECT_CONTINUE        = "HTTP_SERVER_EXPECT_CONTINUE"
  private[service] val HTTP_SERVER_FLUSH_CONSOLIDATION    = "HTTP_SERVER_FLUSH_CONSOLIDATION"
  private[service] val CLIENT_INBOUND_HANDLER             = "CLIENT_INBOUND_HANDLER"
  private[service] val WEB_SOCKET_CLIENT_PROTOCOL_HANDLER = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
  private[service] val HTTP_REQUEST_DECOMPRESSION         = "HTTP_REQUEST_DECOMPRESSION"
  private[service] val LOW_LEVEL_LOGGING                  = "LOW_LEVEL_LOGGING"
  private[zhttp] val HTTP_CONTENT_HANDLER                 = "HTTP_CONTENT_HANDLER"

}
