package zio.http.netty.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  ChannelInitializer,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http.{HttpClientCodec, HttpContentDecompressor}
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.proxy.HttpProxyHandler
import zio._
import zio.http.URL.Location
import zio.http._
import zio.http.logging.LogLevel
import zio.http.netty.NettyFutureExecutor
import zio.http.service._
import zio.http.service.logging.LogLevelTransform.LogLevelWrapper

import java.net.InetSocketAddress

trait ConnectionPool {
  def get(
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
    localAddress: Option[InetSocketAddress] = None,
  ): ZIO[Scope, Throwable, JChannel]

  def invalidate(channel: JChannel): ZIO[Any, Nothing, Unit]

  def enableKeepAlive: Boolean
}

object ConnectionPool {
  private val log = Log.withTags("Client", "Channel")

  protected def createChannel(
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
    localAddress: Option[InetSocketAddress],
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
            ClientSSLConverter
              .toNettySSLContext(sslOptions)
              .newHandler(ch.alloc, location.host, location.port),
          )
        }

        // Adding default client channel handlers
        // Defaults from netty:
        //   maxInitialLineLength=4096
        //   maxHeaderSize=8192
        //   maxChunkSize=8192
        // and we add: failOnMissingResponse=true
        // This way, if the server closes the connection before the whole response has been sent,
        // we get an error. (We can also handle the channelInactive callback, but since for now
        // we always buffer the whole HTTP response we can letty Netty take care of this)
        pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec(4096, maxHeaderSize, 8192, true))

        // HttpContentDecompressor
        if (decompression.enabled)
          pipeline.addLast(
            HTTP_REQUEST_DECOMPRESSION,
            new HttpContentDecompressor(decompression.strict),
          )

        ()
      }
    }

    ZIO.attempt {
      val bootstrap = new Bootstrap()
        .channelFactory(channelFactory)
        .group(eventLoopGroup)
        .remoteAddress(new InetSocketAddress(location.host, location.port))
        .handler(initializer)
      (localAddress match {
        case Some(addr) => bootstrap.localAddress(addr)
        case _          => bootstrap
      }).connect()
    }.flatMap { channelFuture =>
      NettyFutureExecutor.executed(channelFuture) *>
        ZIO
          .attempt(channelFuture.channel())
    }
  }

  private class NoConnectionPool(channelFactory: JChannelFactory[JChannel], eventLoopGroup: JEventLoopGroup)
      extends ConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxHeaderSize: Int,
      decompression: Decompression,
      localAddress: Option[InetSocketAddress] = None,
    ): ZIO[Scope, Throwable, JChannel] =
      createChannel(
        channelFactory,
        eventLoopGroup,
        location,
        proxy,
        sslOptions,
        maxHeaderSize,
        decompression,
        localAddress,
      )

    override def invalidate(channel: JChannel): ZIO[Any, Nothing, Unit] =
      ZIO.unit

    override def enableKeepAlive: Boolean =
      false
  }

  case class PoolKey(
    location: Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
  )

  private class ZioConnectionPool(
    pool: ZKeyedPool[Throwable, PoolKey, JChannel],
  ) extends ConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxHeaderSize: Int,
      decompression: Decompression,
      localAddress: Option[InetSocketAddress] = None,
    ): ZIO[Scope, Throwable, JChannel] =
      ZIO.uninterruptibleMask { restore =>
        restore(
          pool
            .get(PoolKey(location, proxy, sslOptions, maxHeaderSize, decompression)),
        )
      }

    override def invalidate(channel: JChannel): ZIO[Any, Nothing, Unit] =
      pool.invalidate(channel)

    override def enableKeepAlive: Boolean = true
  }

  val disabled: ZLayer[EventLoopGroup with ChannelFactory, Nothing, ConnectionPool] =
    ZLayer {
      createDisabled
    }

  def fromConfig: ZLayer[ConnectionPoolConfig with ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[ConnectionPoolConfig]
        pool   <- config match {
          case ConnectionPoolConfig.Disabled                         =>
            createDisabled
          case ConnectionPoolConfig.Fixed(size)                      =>
            createFixed(size)
          case ConnectionPoolConfig.FixedPerHost(sizes, default)     =>
            createFixedPerHost(location => sizes.getOrElse(location, default).size)
          case ConnectionPoolConfig.Dynamic(minimum, maximum, ttl)   =>
            createDynamic(minimum, maximum, ttl)
          case ConnectionPoolConfig.DynamicPerHost(configs, default) =>
            createDynamicPerHost(
              location => configs.getOrElse(location, default).minimum,
              location => configs.getOrElse(location, default).maximum,
              location => configs.getOrElse(location, default).ttl,
            )
        }
      } yield pool
    }

  def auto: ZLayer[ClientConfig with ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.service[ClientConfig].project(_.connectionPool) >>> fromConfig

  def fixed(size: Int): ZLayer[ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.scoped[ChannelFactory with EventLoopGroup] {
      createFixed(size)
    }

  def fixedPerHost(
    size: URL.Location.Absolute => Int,
  ): ZLayer[ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.scoped[ChannelFactory with EventLoopGroup] {
      createFixedPerHost(size)
    }

  def dynamic(
    min: Int,
    max: Int,
    ttl: Duration,
  ): ZLayer[ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.scoped[ChannelFactory with EventLoopGroup] {
      createDynamic(min, max, ttl)
    }

  def dynamicPerHost(
    min: URL.Location.Absolute => Int,
    max: URL.Location.Absolute => Int,
    ttl: URL.Location.Absolute => Duration,
  ): ZLayer[ChannelFactory with EventLoopGroup, Throwable, ConnectionPool] =
    ZLayer.scoped[ChannelFactory with EventLoopGroup] {
      createDynamicPerHost(min, max, ttl)
    }

  private def createDisabled: ZIO[EventLoopGroup with ChannelFactory, Nothing, ConnectionPool] =
    for {
      channelFactory <- ZIO.service[ChannelFactory]
      eventLoopGroup <- ZIO.service[EventLoopGroup]

    } yield new NoConnectionPool(channelFactory, eventLoopGroup)

  private def createFixed(size: Int): ZIO[Scope with ChannelFactory with EventLoopGroup, Nothing, ConnectionPool] =
    createFixedPerHost(_ => size)

  private def createFixedPerHost(
    size: URL.Location.Absolute => Int,
  ): ZIO[Scope with ChannelFactory with EventLoopGroup, Nothing, ConnectionPool] =
    for {
      channelFactory <- ZIO.service[ChannelFactory]
      eventLoopGroup <- ZIO.service[EventLoopGroup]
      poolPromise    <- Promise.make[Nothing, ZKeyedPool[Throwable, PoolKey, JChannel]]
      keyedPool      <- ZKeyedPool.make(
        (key: PoolKey) =>
          ZIO.uninterruptibleMask { restore =>
            createChannel(
              channelFactory,
              eventLoopGroup,
              key.location,
              key.proxy,
              key.sslOptions,
              key.maxHeaderSize,
              key.decompression,
              None,
            ).tap { channel =>
              restore(
                NettyFutureExecutor.executed(channel.closeFuture()),
              ).zipRight(
                poolPromise.await.flatMap(_.invalidate(channel)),
              ).forkDaemon
            }
          },
        (key: PoolKey) => size(key.location),
      )
      _              <- poolPromise.succeed(keyedPool)
    } yield new ZioConnectionPool(keyedPool)

  private def createDynamic(
    min: Int,
    max: Int,
    ttl: Duration,
  ): ZIO[Scope with ChannelFactory with EventLoopGroup, Nothing, ConnectionPool] =
    createDynamicPerHost(_ => min, _ => max, _ => ttl)

  private def createDynamicPerHost(
    min: URL.Location.Absolute => Int,
    max: URL.Location.Absolute => Int,
    ttl: URL.Location.Absolute => Duration,
  ): ZIO[Scope with ChannelFactory with EventLoopGroup, Nothing, ConnectionPool] =
    for {
      channelFactory <- ZIO.service[ChannelFactory]
      eventLoopGroup <- ZIO.service[EventLoopGroup]
      poolPromise    <- Promise.make[Nothing, ZKeyedPool[Throwable, PoolKey, JChannel]]
      keyedPool      <- ZKeyedPool.make(
        (key: PoolKey) =>
          ZIO.uninterruptibleMask { restore =>
            createChannel(
              channelFactory,
              eventLoopGroup,
              key.location,
              key.proxy,
              key.sslOptions,
              key.maxHeaderSize,
              key.decompression,
              None,
            ).tap { channel =>
              restore(
                NettyFutureExecutor.executed(channel.closeFuture()),
              ).zipRight(
                poolPromise.await.flatMap(_.invalidate(channel)),
              ).forkDaemon
            }
          },
        (key: PoolKey) => min(key.location) to max(key.location),
        (key: PoolKey) => ttl(key.location),
      )
      activeChannels <- Ref.make(Set.empty[JChannel])
    } yield new ZioConnectionPool(keyedPool)
}
