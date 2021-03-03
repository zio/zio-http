package zhttp.service

import io.netty.{channel => jChannel}
import zio.Has

package object netty {
  private[netty] val AUTO_RELEASE_REQUEST = false
  private[netty] val SERVER_CODEC_HANDLER = "SERVER_CODEC"
  private[netty] val OBJECT_AGGREGATOR    = "OBJECT_AGGREGATOR"
  private[netty] val HTTP_REQUEST_HANDLER = "HTTP_REQUEST"
  private[netty] val WEB_SOCKET_HANDLER   = "WEB_SOCKET_HANDLER"

  type ChannelFactory       = Has[jChannel.ChannelFactory[jChannel.Channel]]
  type EventLoopGroup       = Has[jChannel.EventLoopGroup]
  type ServerChannelFactory = Has[jChannel.ChannelFactory[jChannel.ServerChannel]]

}
