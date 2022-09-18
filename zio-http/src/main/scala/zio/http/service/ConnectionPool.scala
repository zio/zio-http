package zio.http.service

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  ChannelInitializer,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.proxy.HttpProxyHandler
import zio.http.URL.Location
import zio.http.netty.NettyFutureExecutor
import zio.http.netty.client.ClientSSLHandler
import zio.http.netty.client.ClientSSLHandler.ClientSSLOptions
import zio.http.service.logging.LogLevelTransform.LogLevelWrapper
import zio.http.{Proxy, URL}
import zio.logging.LogLevel
import zio.{Duration, Scope, ZIO, ZLayer}

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
      NettyFutureExecutor.executed(channelFuture) *>
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
          NettyFutureExecutor
            .executed(channel.closeFuture())
            .zipRight(
              pool.invalidate(channel),
            )
            .forkDaemon,
        )
  }

  // TODO: these should be scoped layers and not getting scope from outside

  val disabled: ZLayer[EventLoopGroup with ChannelFactory, Nothing, ConnectionPool] =
    ZLayer {
      for {
        channelFactory <- ZIO.service[ChannelFactory]
        eventLoopGroup <- ZIO.service[EventLoopGroup]

      } yield new NoConnectionPool(channelFactory, eventLoopGroup)
    }

  // TODO: "auto" layer to be set up from ClientConfig

  def fixed(size: Int): ZLayer[ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.scoped[ChannelFactory with EventLoopGroup] {
      for {
        channelFactory <- ZIO.service[ChannelFactory]
        eventLoopGroup <- ZIO.service[EventLoopGroup]
        keyedPool      <- ZKeyedPool.make(
          (key: PoolKey) => createChannel(channelFactory, eventLoopGroup, key.location, key.proxy, key.sslOptions),
          size,
        )
      } yield new ZioConnectionPool(keyedPool)
    }

  def dynamic(
    min: Int,
    max: Int,
    ttl: Duration,
  ): ZLayer[ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.scoped[ChannelFactory with EventLoopGroup] {
      for {
        channelFactory <- ZIO.service[ChannelFactory]
        eventLoopGroup <- ZIO.service[EventLoopGroup]
        keyedPool      <- ZKeyedPool.make(
          (key: PoolKey) => createChannel(channelFactory, eventLoopGroup, key.location, key.proxy, key.sslOptions),
          min to max,
          ttl,
        )
      } yield new ZioConnectionPool(keyedPool)
    }
}
