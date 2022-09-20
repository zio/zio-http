package zio.http.netty

import io.netty.channel._
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll._
import io.netty.channel.kqueue._
import io.netty.channel.socket.nio._
import io.netty.incubator.channel.uring._
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object ChannelFactories {

  private[zio] def make[A <: Channel](channel: => A)(implicit trace: Trace): UIO[ChannelFactory[A]] =
    ZIO.succeed(new ChannelFactory[A] {
      override def newChannel(): A = channel
    })

  private[zio] def serverChannel[A <: ServerChannel](channel: => A)(implicit trace: Trace) =
    make[ServerChannel](channel)

  private[zio] def clientChannel(channel: => Channel)(implicit trace: Trace) = make(channel)

  object Server {
    def nio(implicit trace: Trace)    = serverChannel(new NioServerSocketChannel())
    def epoll(implicit trace: Trace)  = serverChannel(new EpollServerSocketChannel())
    def uring(implicit trace: Trace)  = serverChannel(new IOUringServerSocketChannel())
    def kqueue(implicit trace: Trace) = serverChannel(new KQueueServerSocketChannel())

    val fromConfig = {
      implicit val trace: Trace = Trace.empty
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
    def nio(implicit trace: Trace)      = clientChannel(new NioSocketChannel())
    def epoll(implicit trace: Trace)    = clientChannel(new EpollSocketChannel())
    def kqueue(implicit trace: Trace)   = clientChannel(new KQueueSocketChannel())
    def uring(implicit trace: Trace)    = clientChannel(new IOUringSocketChannel())
    def embedded(implicit trace: Trace) = clientChannel(new EmbeddedChannel(false, false))

    implicit val trace: Trace = Trace.empty
    val fromConfig            = ZLayer.fromZIO {
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
