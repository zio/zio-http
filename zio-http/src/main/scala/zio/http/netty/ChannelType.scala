package zio.http.netty

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
