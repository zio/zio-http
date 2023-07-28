/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty.client

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.URL.Location
import zio.http._
import zio.http.netty.{Names, NettyFutureExecutor, NettyProxy}

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  ChannelInitializer,
  ChannelOption,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http.{HttpClientCodec, HttpContentDecompressor}
import io.netty.handler.proxy.HttpProxyHandler
import io.netty.handler.timeout.ReadTimeoutHandler
private[netty] trait NettyConnectionPool extends ConnectionPool[JChannel]

private[netty] object NettyConnectionPool {

  protected def createChannel(
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
    idleTimeout: Option[Duration],
    connectionTimeout: Option[Duration],
    localAddress: Option[InetSocketAddress],
    dnsResolver: DnsResolver,
  )(implicit trace: Trace): ZIO[Any, Throwable, JChannel] = {
    val initializer = new ChannelInitializer[JChannel] {
      override def initChannel(ch: JChannel): Unit = {
        val pipeline = ch.pipeline()

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

        idleTimeout.foreach { timeout =>
          pipeline.addLast(Names.ReadTimeoutHandler, new ReadTimeoutHandler(timeout.toMillis, TimeUnit.MILLISECONDS))
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

    for {
      resolvedHosts <- dnsResolver.resolve(location.host)
      pickedHost    <- Random.nextIntBounded(resolvedHosts.size)
      host = resolvedHosts(pickedHost)
      channelFuture <- ZIO.attempt {
        val bootstrap = new Bootstrap()
          .channelFactory(channelFactory)
          .group(eventLoopGroup)
          .remoteAddress(new InetSocketAddress(host, location.port))
          .withOption[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout.map(_.toMillis.toInt))
          .handler(initializer)
        (localAddress match {
          case Some(addr) => bootstrap.localAddress(addr)
          case _          => bootstrap
        }).connect()
      }
      _             <- NettyFutureExecutor.executed(channelFuture)
      result        <- ZIO.attempt(channelFuture.channel())
    } yield result
  }

  private final class NoNettyConnectionPool(
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    dnsResolver: DnsResolver,
  ) extends NettyConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxHeaderSize: Int,
      decompression: Decompression,
      idleTimeout: Option[Duration],
      connectionTimeout: Option[Duration],
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
        idleTimeout,
        connectionTimeout,
        localAddress,
        dnsResolver,
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
    idleTimeout: Option[Duration],
    connectionTimeout: Option[Duration],
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
      idleTimeout: Option[Duration],
      connectionTimeout: Option[Duration],
      localAddress: Option[InetSocketAddress] = None,
    )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] =
      pool
        .get(PoolKey(location, proxy, sslOptions, maxHeaderSize, decompression, idleTimeout, connectionTimeout))

    override def invalidate(channel: JChannel)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      pool.invalidate(channel)

    override def enableKeepAlive: Boolean = true
  }

  def fromConfig(
    config: ConnectionPoolConfig,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver with DnsResolver, Nothing, NettyConnectionPool] =
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

  private def createDisabled(implicit
    trace: Trace,
  ): ZIO[NettyClientDriver with DnsResolver, Nothing, NettyConnectionPool] =
    for {
      driver      <- ZIO.service[NettyClientDriver]
      dnsResolver <- ZIO.service[DnsResolver]
    } yield new NoNettyConnectionPool(driver.channelFactory, driver.eventLoopGroup, dnsResolver)

  private def createFixed(size: Int)(implicit
    trace: Trace,
  ): ZIO[Scope with NettyClientDriver with DnsResolver, Nothing, NettyConnectionPool] =
    createFixedPerHost(_ => size)

  private def createFixedPerHost(
    size: URL.Location.Absolute => Int,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver with DnsResolver, Nothing, NettyConnectionPool] =
    for {
      driver      <- ZIO.service[NettyClientDriver]
      dnsResolver <- ZIO.service[DnsResolver]
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
          key.idleTimeout,
          key.connectionTimeout,
          None,
          dnsResolver,
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
    } yield new ZioNettyConnectionPool(keyedPool)

  private def createDynamic(
    min: Int,
    max: Int,
    ttl: Duration,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver with DnsResolver, Nothing, NettyConnectionPool] =
    createDynamicPerHost(_ => min, _ => max, _ => ttl)

  private def createDynamicPerHost(
    min: URL.Location.Absolute => Int,
    max: URL.Location.Absolute => Int,
    ttl: URL.Location.Absolute => Duration,
  )(implicit trace: Trace): ZIO[Scope with NettyClientDriver with DnsResolver, Nothing, NettyConnectionPool] =
    for {
      driver      <- ZIO.service[NettyClientDriver]
      dnsResolver <- ZIO.service[DnsResolver]
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
          key.idleTimeout,
          key.connectionTimeout,
          None,
          dnsResolver,
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
    } yield new ZioNettyConnectionPool(keyedPool)

  implicit final class BootstrapSyntax(val bootstrap: Bootstrap) extends AnyVal {
    def withOption[T](option: ChannelOption[T], value: Option[T]): Bootstrap =
      value match {
        case Some(value) => bootstrap.option(option, value)
        case None        => bootstrap
      }
  }
}
