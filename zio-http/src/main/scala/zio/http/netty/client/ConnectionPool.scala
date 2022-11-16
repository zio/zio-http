package zio.http.netty.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel => JChannel, ChannelFactory => JChannelFactory, ChannelInitializer, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.{HttpClientCodec, HttpContentDecompressor}
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.proxy.HttpProxyHandler
import zio.http.URL.Location
import zio.http._
import zio.http.logging.LogLevel
import zio.http.netty.NettyFutureExecutor
import zio.http.service._
import zio.http.service.logging.LogLevelTransform.LogLevelWrapper
import zio.{Duration, Scope, ZIO, ZKeyedPool, ZLayer}

import java.net.{InetSocketAddress, SocketAddress}

trait ConnectionPool {
  def get(
    location: URL.Location.Absolute,
    socketAddress: Option[SocketAddress],
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
  ): ZIO[Scope, Throwable, JChannel]

  def invalidate(channel: JChannel): ZIO[Any, Nothing, Unit]
}

object ConnectionPool {
  private val log = Log.withTags("Client", "Channel")

  protected def createChannel(
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    socketAddress: SocketAddress,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
    isSecure: Boolean,
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
              proxy.encode.getOrElse(new HttpProxyHandler(socketAddress)),
            )
          case None        =>
        }

        if (isSecure) {
          val (host, port) = socketAddress match {
            case address: InetSocketAddress => (address.getHostString, address.getPort)
            case _                          => ("localhost", 80)
          }
          pipeline.addLast(
            SSL_HANDLER,
            ClientSSLConverter
              .toNettySSLContext(sslOptions)
              .newHandler(ch.alloc, host, port),
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
      new Bootstrap()
        .channelFactory(channelFactory)
        .group(eventLoopGroup)
        .remoteAddress(socketAddress)
        .handler(initializer)
        .connect()
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
      socketAddress: Option[SocketAddress],
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxHeaderSize: Int,
      decompression: Decompression,
    ): ZIO[Scope, Throwable, JChannel] =
      createChannel(
        channelFactory,
        eventLoopGroup,
        socketAddress.getOrElse(new InetSocketAddress(location.host, location.port)),
        proxy,
        sslOptions,
        maxHeaderSize,
        decompression,
        location.scheme.isSecure,
      )

    override def invalidate(channel: JChannel): ZIO[Any, Nothing, Unit] =
      ZIO.unit
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
      socketAddress: Option[SocketAddress],
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxHeaderSize: Int,
      decompression: Decompression,
    ): ZIO[Scope, Throwable, JChannel] =
      ZIO.uninterruptibleMask { restore =>
        restore(
          pool
            .get(PoolKey(location, proxy, sslOptions, maxHeaderSize, decompression)),
        ).tap { channel =>
          restore(
            NettyFutureExecutor.executed(channel.closeFuture()),
          ).zipRight(
            pool.invalidate(channel),
          ).forkDaemon
        }
      }

    override def invalidate(channel: JChannel): ZIO[Any, Nothing, Unit] =
      pool.invalidate(channel)
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
      keyedPool      <- ZKeyedPool.make(
        (key: PoolKey) =>
          createChannel(
            channelFactory,
            eventLoopGroup,
            new InetSocketAddress(key.location.host, key.location.port),
            key.proxy,
            key.sslOptions,
            key.maxHeaderSize,
            key.decompression,
            key.location.scheme.isSecure,
          ),
        (key: PoolKey) => size(key.location),
      )
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
      keyedPool      <- ZKeyedPool.make(
        (key: PoolKey) =>
          createChannel(
            channelFactory,
            eventLoopGroup,
            new InetSocketAddress(key.location.host, key.location.port),
            key.proxy,
            key.sslOptions,
            key.maxHeaderSize,
            key.decompression,
            key.location.scheme.isSecure,
          ),
        (key: PoolKey) => min(key.location) to max(key.location),
        (key: PoolKey) => ttl(key.location),
      )
    } yield new ZioConnectionPool(keyedPool)
}
