package zhttp.service.server

import io.netty.channel.epoll.{Epoll => JEpoll}
import io.netty.channel.kqueue.{KQueue => JKQueue}
import zhttp.core._
import zhttp.service.ServerChannelFactory
import zio.{UIO, ZLayer}

object ServerChannelFactory {
  def nio: ZLayer[Any, Nothing, ServerChannelFactory] = Live.nio.toLayer

  def epoll: ZLayer[Any, Nothing, ServerChannelFactory] = Live.epoll.toLayer

  def auto: ZLayer[Any, Nothing, ServerChannelFactory] = Live.auto.toLayer

  object Live {
    def nio: UIO[JChannelFactory[JServerChannel]] =
      UIO(() => new JNioServerSocketChannel())

    def epoll: UIO[JChannelFactory[JServerChannel]] =
      UIO(() => new JEpollServerSocketChannel())

    def kQueue: UIO[JChannelFactory[JServerChannel]] =
      UIO(() => new JKQueueServerSocketChannel())

    def auto: UIO[JChannelFactory[JServerChannel]] =
      if (JEpoll.isAvailable) epoll
      else if (JKQueue.isAvailable) kQueue
      else nio
  }
}
