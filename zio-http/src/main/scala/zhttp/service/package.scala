package zhttp

import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, ServerChannel}
import zio.Has

package object service {
  private[service] val AUTO_RELEASE_REQUEST            = false
  private[service] val SERVER_CODEC_HANDLER            = "SERVER_CODEC"
  private[service] val OBJECT_AGGREGATOR               = "OBJECT_AGGREGATOR"
  private[service] val HTTP_REQUEST_HANDLER            = "HTTP_REQUEST"
  private[service] val HTTP_KEEPALIVE_HANDLER          = "HTTP_KEEPALIVE"
  private[service] val FLOW_CONTROL_HANDLER            = "FLOW_CONTROL_HANDLER"
  private[service] val WEB_SOCKET_HANDLER              = "WEB_SOCKET_HANDLER"
  private[service] val SSL_HANDLER                     = "SSL_HANDLER"
  private[service] val HTTP_ON_HTTPS_HANDLER           = "HTTP_ON_HTTPS_HANDLER"
  private[service] val HTTP_SERVER_CODEC               = "HTTP_SERVER_CODEC"
  private[service] val HTTP_SERVER_EXPECT_CONTINUE     = "HTTP_SERVER_EXPECT_CONTINUE"
  private[service] val HTTP_SERVER_FLUSH_CONSOLIDATION = "HTTP_SERVER_FLUSH_CONSOLIDATION"

  type ChannelFactory       = Has[JChannelFactory[Channel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[ServerChannel]]
  type UServer              = Server[Any, Nothing]
}
