package zio.http.netty.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  ChannelInitializer,
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http.{HttpClientCodec, HttpContentDecompressor}
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.proxy.HttpProxyHandler
import zio._
import zio.http.URL.Location
import zio.http._
import zio.http.logging.LogLevel
import zio.http.netty.{Names, NettyFutureExecutor, NettyProxy}
import zio.http.service._
import zio.http.service.logging.LogLevelTransform.LogLevelWrapper
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok

import java.net.InetSocketAddress

trait NettyConnectionPool extends ConnectionPool[JChannel]

object NettyConnectionPool {
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
  )(implicit trace: Trace): ZIO[Any, Throwable, JChannel] = {
    val initializer = new ChannelInitializer[JChannel] {
      override def initChannel(ch: JChannel): Unit = {
        val pipeline = ch.pipeline()

        if (EnableNettyLogging) {
          import io.netty.util.internal.logging.InternalLoggerFactory
          InternalLoggerFactory.setDefaultFactory(zio.http.service.logging.NettyLoggerFactory(log))
          pipeline.addLast(Names.LowLevelLogging, new LoggingHandler(LogLevel.Debug.toNettyLogLevel))
        }

        proxy match {
          case Some(proxy) =>
            pipeline.addLast(
              Names.ProxyHandler,
              NettyProxy
                .fromProxy(proxy)
                .encode
                .getOrElse(new HttpProxyHandler(new InetSocketAddress(location.host, location.port))),
            )
          case None        =>
        }

        if (location.scheme.isSecure) {
          pipeline.addLast(
            Names.SSLHandler,
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
        pipeline.addLast(Names.HttpClientCodec, new HttpClientCodec(4096, maxHeaderSize, 8192, true))

        // HttpContentDecompressor
        if (decompression.enabled)
          pipeline.addLast(
            Names.HttpRequestDecompression,
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

  private final class NoNettyConnectionPool(channelFactory: JChannelFactory[JChannel], eventLoopGroup: JEventLoopGroup)
      extends NettyConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxHeaderSize: Int,
      decompression: Decompression,
      localAddress: Option[InetSocketAddress] = None,
    )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] =
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

    override def invalidate(channel: JChannel)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
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

  private final class ZioNettyConnectionPool(
    pool: ZKeyedPool[Throwable, PoolKey, JChannel],
  ) extends NettyConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxHeaderSize: Int,
      decompression: Decompression,
      localAddress: Option[InetSocketAddress] = None,
    )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] =
      pool
        .get(PoolKey(location, proxy, sslOptions, maxHeaderSize, decompression))

    override def invalidate(channel: JChannel)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      pool.invalidate(channel)

    override def enableKeepAlive: Boolean = true
  }

  def fromConfig(
    config: ConnectionPoolConfig,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver, Nothing, NettyConnectionPool] =
    for {
      pool <- config match {
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

  private def createDisabled(implicit trace: Trace): ZIO[NettyClientDriver, Nothing, NettyConnectionPool] =
    for {
      driver <- ZIO.service[NettyClientDriver]
    } yield new NoNettyConnectionPool(driver.channelFactory, driver.eventLoopGroup)

  private def createFixed(size: Int)(implicit
    trace: Trace,
  ): ZIO[Scope with NettyClientDriver, Nothing, NettyConnectionPool] =
    createFixedPerHost(_ => size)

  private def createFixedPerHost(
    size: URL.Location.Absolute => Int,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver, Nothing, NettyConnectionPool] =
    for {
      driver      <- ZIO.service[NettyClientDriver]
      poolPromise <- Promise.make[Nothing, ZKeyedPool[Throwable, PoolKey, JChannel]]
      poolFn = (key: PoolKey) =>
        createChannel(
          driver.channelFactory,
          driver.eventLoopGroup,
          key.location,
          key.proxy,
          key.sslOptions,
          key.maxHeaderSize,
          key.decompression,
          None,
        ).tap { channel =>
          NettyFutureExecutor
            .executed(channel.closeFuture())
            .interruptible
            .zipRight(
              poolPromise.await.flatMap(_.invalidate(channel)),
            )
            .forkDaemon
        }.uninterruptible
      keyedPool <- ZKeyedPool
        .make(poolFn, (key: PoolKey) => size(key.location))
        .tap(poolPromise.succeed)
        .tapErrorCause(poolPromise.failCause)
        .interruptible // TODO: Needs a fix in ZKeyedPool to be able to make this uninterruptible
    } yield new ZioNettyConnectionPool(keyedPool)

  private def createDynamic(
    min: Int,
    max: Int,
    ttl: Duration,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver, Nothing, NettyConnectionPool] =
    createDynamicPerHost(_ => min, _ => max, _ => ttl)

  private def createDynamicPerHost(
    min: URL.Location.Absolute => Int,
    max: URL.Location.Absolute => Int,
    ttl: URL.Location.Absolute => Duration,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver, Nothing, NettyConnectionPool] =
    for {
      driver      <- ZIO.service[NettyClientDriver]
      poolPromise <- Promise.make[Nothing, ZKeyedPool[Throwable, PoolKey, JChannel]]
      poolFn = (key: PoolKey) =>
        createChannel(
          driver.channelFactory,
          driver.eventLoopGroup,
          key.location,
          key.proxy,
          key.sslOptions,
          key.maxHeaderSize,
          key.decompression,
          None,
        ).tap { channel =>
          NettyFutureExecutor
            .executed(channel.closeFuture())
            .interruptible
            .zipRight(
              poolPromise.await.flatMap(_.invalidate(channel)),
            )
            .forkDaemon
        }.uninterruptible
      keyedPool <- ZKeyedPool
        .make(poolFn, (key: PoolKey) => min(key.location) to max(key.location), (key: PoolKey) => ttl(key.location))
        .tap(poolPromise.succeed)
        .tapErrorCause(poolPromise.failCause)
        .interruptible // TODO: Needs a fix in ZKeyedPool to be able to make this uninterruptible
    } yield new ZioNettyConnectionPool(keyedPool)
}
