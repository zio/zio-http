package zio.http.service

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  ChannelInitializer,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.proxy.HttpProxyHandler
import zio.http.URL.Location
import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.{ChannelType, ClientConfig, Proxy, URL}
import zio.{Scope, UIO, ZIO, ZLayer}

import java.net.InetSocketAddress

trait ConnectionPool {
  def get(
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLOptions,
  ): ZIO[Any, Throwable, JChannel]
}

object ConnectionPool {
  private trait ConnectionPoolBase extends ConnectionPool {
    val channelFactory: JChannelFactory[JChannel]
    val eventLoopGroup: JEventLoopGroup

    protected def createChannel(
      location: URL.Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLOptions,
    ): ZIO[Any, Throwable, JChannel] = {
      val initializer = new ChannelInitializer[JChannel] {
        override def initChannel(ch: JChannel): Unit = {
          val pipeline = ch.pipeline()

          proxy match {
            case Some(proxy) =>
              pipeline.addLast(
                PROXY_HANDLER,
                proxy.encode.getOrElse(new HttpProxyHandler(new InetSocketAddress(location.host, location.port))),
              )
            case None        =>
          }

          if (location.scheme.isSecure) {
            pipeline.addLast(
              SSL_HANDLER,
              ClientSSLHandler.ssl(sslOptions).newHandler(ch.alloc, location.host, location.port),
            )
          }

          pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec(4096, 8192, 8192, true))

          ()
        }
      }

      ZIO.attempt {
        new Bootstrap()
          .channelFactory(channelFactory)
          .group(eventLoopGroup)
          .remoteAddress(new InetSocketAddress(location.host, location.port))
          .handler(initializer)
          .connect()
      }.flatMap { channelFuture =>
        ZIO.debug("got channel future") *>
          ChannelFuture.unit(channelFuture) *> ZIO.debug("channel future completed") *>
          ZIO.attempt(channelFuture.channel()).debug("channel")
      }
    }
  }

  private class NoConnectionPool(val channelFactory: JChannelFactory[JChannel], val eventLoopGroup: JEventLoopGroup)
      extends ConnectionPoolBase {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLOptions,
    ): ZIO[Any, Throwable, JChannel] =
      createChannel(location, proxy, sslOptions)
  }

  private def channelFactory(config: ClientConfig): UIO[ChannelFactory] = {
    config.channelType match {
      case ChannelType.NIO    => ChannelFactory.Live.nio
      case ChannelType.EPOLL  => ChannelFactory.Live.epoll
      case ChannelType.KQUEUE => ChannelFactory.Live.kQueue
      case ChannelType.URING  => ChannelFactory.Live.uring
      case ChannelType.AUTO   => ChannelFactory.Live.auto
    }
  }

  private def eventLoopGroup(config: ClientConfig): ZIO[Scope, Nothing, EventLoopGroup] = {
    config.channelType match {
      case ChannelType.NIO    => EventLoopGroup.Live.nio(config.nThreads)
      case ChannelType.EPOLL  => EventLoopGroup.Live.epoll(config.nThreads)
      case ChannelType.KQUEUE => EventLoopGroup.Live.kQueue(config.nThreads)
      case ChannelType.URING  => EventLoopGroup.Live.uring(config.nThreads)
      case ChannelType.AUTO   => EventLoopGroup.Live.auto(config.nThreads)
    }
  }

  // TODO: these should be scoped layers and not getting scope from outside

  val disabled: ZLayer[Scope with ClientConfig, Nothing, ConnectionPool] =
    ZLayer {
      for {
        config         <- ZIO.service[ClientConfig]
        channelFactory <- channelFactory(config)
        eventLoopGroup <- eventLoopGroup(config)

      } yield new NoConnectionPool(channelFactory, eventLoopGroup)
    }
}
