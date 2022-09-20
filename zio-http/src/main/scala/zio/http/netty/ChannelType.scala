package zio.http.netty

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait ChannelType

object ChannelType {
  case object NIO extends ChannelType

  case object EPOLL extends ChannelType

  case object KQUEUE extends ChannelType

  case object URING extends ChannelType

  case object AUTO extends ChannelType

  trait Config {
    def channelType: ChannelType
  }

}
