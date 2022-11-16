package zio.http.netty

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait ChannelType

object ChannelType {
  case object NIO extends ChannelType

  case object EPOLL extends ChannelType

  case object KQUEUE extends ChannelType

  case object URING extends ChannelType

  case object AUTO extends ChannelType

  case object LOCAL extends ChannelType

  case object EPOLL_UDS extends ChannelType

  case object KQUEUE_UDS extends ChannelType

  trait Config {
    def channelType: ChannelType
  }

}
