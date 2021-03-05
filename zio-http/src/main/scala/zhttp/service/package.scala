package zhttp

import io.netty.{channel => jChannel}
import zio.Has

package object service {
  private[service] val AUTO_RELEASE_REQUEST = false
  private[service] val SERVER_CODEC_HANDLER = "SERVER_CODEC"
  private[service] val OBJECT_AGGREGATOR    = "OBJECT_AGGREGATOR"
  private[service] val HTTP_REQUEST_HANDLER = "HTTP_REQUEST"
  private[service] val WEB_SOCKET_HANDLER   = "WEB_SOCKET_HANDLER"

  type ChannelFactory       = Has[jChannel.ChannelFactory[jChannel.Channel]]
  type EventLoopGroup       = Has[jChannel.EventLoopGroup]
  type ServerChannelFactory = Has[jChannel.ChannelFactory[jChannel.ServerChannel]]

}
