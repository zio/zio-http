package zhttp.service.netty.server

import io.netty.channel.epoll.{Epoll => JEpoll}
import zhttp.core.netty.{JChannelFactory, JEpollServerSocketChannel, JNioServerSocketChannel, JServerChannel}
import zhttp.service.netty.ServerChannelFactory
import zio.{UIO, ZLayer}

object ServerChannelFactory {
  def nio: ZLayer[Any, Nothing, ServerChannelFactory] = Live.nio.toLayer

  def epoll: ZLayer[Any, Nothing, ServerChannelFactory] = Live.epoll.toLayer

  object Live {
    def nio: UIO[JChannelFactory[JServerChannel]] =
      UIO(() => new JNioServerSocketChannel())

    def epoll: UIO[JChannelFactory[JServerChannel]] =
      UIO(() => new JEpollServerSocketChannel())

    def auto: UIO[JChannelFactory[JServerChannel]] =
      if (JEpoll.isAvailable) epoll else nio
  }
}
