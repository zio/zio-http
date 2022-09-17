package zio.http.service

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel => JChannel, ChannelFactory => JChannelFactory, ChannelInitializer, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.proxy.HttpProxyHandler
import zio.http.URL.Location
import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.service.logging.LogLevelTransform.LogLevelWrapper
import zio.http.{ChannelType, ClientConfig, Proxy, URL}
import zio.logging.LogLevel
import zio.{Duration, Scope, UIO, ZIO, ZLayer}

import java.net.InetSocketAddress

trait ConnectionPool {
  def get(
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLOptions,
  ): ZIO[Scope, Throwable, JChannel]
}

object ConnectionPool {
  private val log = Log.withTags("Client", "Channel")

  protected def createChannel(
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLOptions,
  ): ZIO[Any, Throwable, JChannel] = {
    val initializer = new ChannelInitializer[JChannel] {
      override def initChannel(ch: JChannel): Unit = {
        val pipeline = ch.pipeline()

        if (EnableNettyLogging) {
          import io.netty.util.internal.logging.InternalLoggerFactory
          InternalLoggerFactory.setDefaultFactory(zio.http.service.logging.NettyLoggerFactory(log))
          pipeline.addLast(LOW_LEVEL_LOGGING, new LoggingHandler(LogLevel.Debug.toNettyLogLevel))
        }

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
      ChannelFuture.unit(channelFuture) *>
        ZIO.attempt(channelFuture.channel())
    }
  }

  private class NoConnectionPool(channelFactory: JChannelFactory[JChannel], eventLoopGroup: JEventLoopGroup)
      extends ConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLOptions,
    ): ZIO[Scope, Throwable, JChannel] =
      createChannel(channelFactory, eventLoopGroup, location, proxy, sslOptions)
  }

  case class PoolKey(location: Location.Absolute, proxy: Option[Proxy], sslOptions: ClientSSLOptions)

  private class ZioConnectionPool(
    pool: ZKeyedPool[Throwable, PoolKey, JChannel],
  ) extends ConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLOptions,
    ): ZIO[Scope, Throwable, JChannel] =
      pool
        .get(PoolKey(location, proxy, sslOptions))
        .tap(channel =>
          ChannelFuture
            .unit(channel.closeFuture())
            .zipRight(
              pool.invalidate(channel),
            )
            .forkDaemon,
        )
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

  // TODO: "auto" layer to be set up from ClientConfig

  def fixed(size: Int): ZLayer[Scope with ClientConfig, Throwable, ConnectionPool] =
    ZLayer.scoped {
      for {
        config         <- ZIO.service[ClientConfig]
        channelFactory <- channelFactory(config)
        eventLoopGroup <- eventLoopGroup(config)
        keyedPool      <- ZKeyedPool.make(
          (key: PoolKey) => createChannel(channelFactory, eventLoopGroup, key.location, key.proxy, key.sslOptions),
          size,
        )
      } yield new ZioConnectionPool(keyedPool)
    }

  def dynamic(min: Int, max: Int, ttl: Duration): ZLayer[Scope with ClientConfig, Throwable, ConnectionPool] =
    ZLayer.scoped {
      for {
        config         <- ZIO.service[ClientConfig]
        channelFactory <- channelFactory(config)
        eventLoopGroup <- eventLoopGroup(config)
        keyedPool      <- ZKeyedPool.make(
          (key: PoolKey) => createChannel(channelFactory, eventLoopGroup, key.location, key.proxy, key.sslOptions),
          min to max,
          ttl,
        )
      } yield new ZioConnectionPool(keyedPool)
    }
}
