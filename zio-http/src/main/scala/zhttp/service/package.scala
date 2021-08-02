package zhttp

import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, ServerChannel}
import zio.Has

package object service {
  private[service] val AUTO_RELEASE_REQUEST   = false
  private[service] val SERVER_CODEC_HANDLER   = "SERVER_CODEC"
  private[service] val OBJECT_AGGREGATOR      = "OBJECT_AGGREGATOR"
  private[service] val HTTP_REQUEST_HANDLER   = "HTTP_REQUEST"
  private[service] val HTTP_KEEPALIVE_HANDLER = "HTTP_KEEPALIVE"
  private[service] val WEB_SOCKET_HANDLER     = "WEB_SOCKET_HANDLER"
  private[service] val SSL_HANDLER            = "SSL_HANDLER"
  private[service] val HTTP_ON_HTTPS_HANDLER  = "HTTP_ON_HTTPS_HANDLER"

  type ChannelFactory       = Has[JChannelFactory[Channel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[ServerChannel]]
  type UServer              = Server[Any, Nothing]
}
