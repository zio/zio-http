package zhttp

import zhttp.core._
import zio.Has

package object service {
  private[service] val AUTO_RELEASE_REQUEST   = false
  private[service] val SERVER_CODEC_HANDLER   = "SERVER_CODEC"
  private[service] val OBJECT_AGGREGATOR      = "OBJECT_AGGREGATOR"
  private[service] val HTTP_REQUEST_HANDLER   = "HTTP_REQUEST"
  private[service] val HTTP_KEEPALIVE_HANDLER = "HTTP_KEEPALIVE"
  private[service] val WEB_SOCKET_HANDLER     = "WEB_SOCKET_HANDLER"
  private[service] val STREAM_HANDLER         = "STREAM_HANDLER"
  private[service] val CONTINUE_100_HANDLER   = "CONTINUE_100_HANDLER"
  private[service] val FLOW_CONTROL_HANDLER   = "FLOW_CONTROL_HANDLER"
  private[service] val FULL_REQUEST_HANDLER   = "FULL_REQUEST_HANDLER"

  type ChannelFactory       = Has[JChannelFactory[JChannel]]
  type EventLoopGroup       = Has[JEventLoopGroup]
  type ServerChannelFactory = Has[JChannelFactory[JServerChannel]]
  type UServer              = Server[Any, Nothing]
}
