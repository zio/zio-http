package zhttp.service.server

import io.netty.channel.epoll.{Epoll => JEpoll}
import zhttp.core.{JChannelFactory, JEpollServerSocketChannel, JNioServerSocketChannel, JServerChannel}

import io.netty.incubator.channel.uring.{IOUringServerSocketChannel => JIOUringServerSocketChannel}
import io.netty.incubator.channel.uring.{IOUring => JIOUring}
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

    def uring: UIO[JChannelFactory[JServerChannel]] =
      UIO(() => new JIOUringServerSocketChannel())

    def auto: UIO[JChannelFactory[JServerChannel]] =
      if (JIOUring.isAvailable) uring else if (JEpoll.isAvailable) epoll else nio
  }
}
