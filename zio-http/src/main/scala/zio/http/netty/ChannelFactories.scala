package zio.http.netty

import io.netty.channel._
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll._
import io.netty.channel.kqueue._
import io.netty.channel.socket.nio._
import io.netty.incubator.channel.uring._
import zio._

object ChannelFactories {

  private[zio] def make[A <: Channel](channel: => A): UIO[ChannelFactory[A]] =
    ZIO.succeed(new ChannelFactory[A] {
      override def newChannel(): A = channel
    })

  private[zio] def serverChannel[A <: ServerChannel](channel: => A) =
    make[ServerChannel](channel)

  private[zio] def clientChannel(channel: => Channel) = make(channel)

  object Server {
    def nio    = serverChannel(new NioServerSocketChannel())
    def epoll  = serverChannel(new EpollServerSocketChannel())
    def uring  = serverChannel(new IOUringServerSocketChannel())
    def kqueue = serverChannel(new KQueueServerSocketChannel())

    val fromConfig = {
      ZLayer.fromZIO {
        ZIO.service[ChannelType.Config].flatMap {
          _.channelType match {
            case ChannelType.NIO    => nio
            case ChannelType.EPOLL  => epoll
            case ChannelType.KQUEUE => kqueue
            case ChannelType.URING  => uring
            case ChannelType.AUTO   =>
              if (Epoll.isAvailable) epoll
              else if (KQueue.isAvailable) kqueue
              else nio
          }
        }
      }
    }
  }

  object Client {
    def nio      = clientChannel(new NioSocketChannel())
    def epoll    = clientChannel(new EpollSocketChannel())
    def kqueue   = clientChannel(new KQueueSocketChannel())
    def uring    = clientChannel(new IOUringSocketChannel())
    def embedded = clientChannel(new EmbeddedChannel(false, false))

    val fromConfig: ZLayer[ChannelType.Config, Nothing, ChannelFactory[Channel]] =
      ZLayer.fromZIO {
        ZIO.service[ChannelType.Config].flatMap {
          _.channelType match {
            case ChannelType.NIO    => nio
            case ChannelType.EPOLL  => epoll
            case ChannelType.KQUEUE => kqueue
            case ChannelType.URING  => uring
            case ChannelType.AUTO   =>
              if (Epoll.isAvailable) epoll
              else if (KQueue.isAvailable) kqueue
              else nio
          }
        }
      }
  }

}
