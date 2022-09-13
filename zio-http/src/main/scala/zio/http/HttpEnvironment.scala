package zio.http

import zio.UIO
import io.netty.channel.{
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  EventLoopGroup => JEventLoopGroup,
  ServerChannel => JServerChannel,
}

object HttpEnvironment {

  sealed trait Transport
  object Transport {
    final case object AUTO   extends Transport
    final case object NIO    extends Transport
    final case object EPOLL  extends Transport
    final case object KQUEUE extends Transport
    final case object URING  extends Transport
  }

  trait HttpClientEnv {
    def clientChannelFactory: UIO[JChannelFactory[JChannel]]
    def bootStrapLoopGroup: UIO[JEventLoopGroup]

    def setThreads(n: Int): UIO[Unit]
    def setTransport(transport: Transport): UIO[Unit]
  }

  trait HttpServerEnv {
    def serverChannelFactory: UIO[JChannelFactory[JServerChannel]]
    def bootStrapLoopGroup: UIO[JEventLoopGroup]

    def setThreads(n: Int): UIO[Unit]
    def setTransport(transport: Transport): UIO[Unit]
  }

  final case class HttpServerEnvironment(
    channelFactory: JChannelFactory[JServerChannel],
    bootStrapLoopGroup: JEventLoopGroup,
    threads: Int,
  )

  final case class HttpClientEnvironment(
    channelFactory: JChannelFactory[JChannel],
    bootStrapLoopGroup: JEventLoopGroup,
    threads: Int,
  )

}
