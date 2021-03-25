package zhttp.service

import io.netty.channel.epoll.{Epoll => JEpoll}
import io.netty.channel.kqueue.{KQueue => JKQueue}
import zhttp.core._
import zio.{UIO, ZLayer}

object ChannelFactory {
  def nio: ZLayer[Any, Nothing, ChannelFactory] = Live.nio.toLayer

  def epoll: ZLayer[Any, Nothing, ChannelFactory] = Live.epoll.toLayer

  def embedded: ZLayer[Any, Nothing, ChannelFactory] = Live.embedded.toLayer

  def auto: ZLayer[Any, Nothing, ChannelFactory] = Live.auto.toLayer

  object Live {
    def nio: UIO[JChannelFactory[JChannel]] = {
      val sam: JChannelFactory[JChannel] = () => new JNioSocketChannel()
      UIO(sam)
    }

    def epoll: UIO[JChannelFactory[JChannel]] = {
      val sam: JChannelFactory[JChannel] = () => new JEpollSocketChannel()
      UIO(sam)
    }

    def kQueue: UIO[JChannelFactory[JChannel]] = {
      val sam: JChannelFactory[JChannel] = () => new JKQueueSocketChannel()
      UIO(sam)
    }

    def embedded: UIO[JChannelFactory[JChannel]] = {
      val sam: JChannelFactory[JChannel] = () => new JEmbeddedChannel(false, false)
      UIO(sam)
    }

    def auto: UIO[JChannelFactory[JChannel]] =
      if (JEpoll.isAvailable) epoll
      else if (JKQueue.isAvailable) kQueue
      else nio
  }

}
