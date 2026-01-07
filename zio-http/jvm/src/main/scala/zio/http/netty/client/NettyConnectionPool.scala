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

import java.net.{Inet6Address, InetAddress, InetSocketAddress}
import java.util.concurrent.TimeUnit

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.URL.Location
import zio.http._
import zio.http.netty.{Names, NettyFutureExecutor, NettyProxy, NettyRuntime}

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel => JChannel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, _}
import io.netty.handler.codec.http.{HttpClientCodec, HttpContentDecompressor}
import io.netty.handler.proxy.HttpProxyHandler
import io.netty.handler.timeout.{ReadTimeoutException, ReadTimeoutHandler}

private[netty] trait NettyConnectionPool extends ConnectionPool[JChannel]

private[netty] object NettyConnectionPool {

  private val HappyEyeballsDelay: Duration = 250.millis

  protected def createChannel(
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    nettyRuntime: NettyRuntime,
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxInitialLineLength: Int,
    maxHeaderSize: Int,
    decompression: Decompression,
    idleTimeout: Option[Duration],
    connectionTimeout: Option[Duration],
    localAddress: Option[InetSocketAddress],
    dnsResolver: DnsResolver,
  )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] = {
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

        if (location.scheme.isSecure.getOrElse(false)) {
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

        pipeline.addLast(Names.ClientReadTimeoutErrorHandler, new ReadTimeoutErrorHandler(nettyRuntime))

        // Adding default client channel handlers
        // Defaults from netty:
        //   maxInitialLineLength=4096
        //   maxHeaderSize=8192
        //   maxChunkSize=8192
        // and we add: failOnMissingResponse=true
        // This way, if the server closes the connection before the whole response has been sent,
        // we get an error. (We can also handle the channelInactive callback, but since for now
        // we always buffer the whole HTTP response we can letty Netty take care of this)
        pipeline.addLast(Names.HttpClientCodec, new HttpClientCodec(maxInitialLineLength, maxHeaderSize, 8192, true))

        // HttpContentDecompressor
        if (decompression.enabled)
          pipeline.addLast(
            Names.HttpRequestDecompression,
            new HttpContentDecompressor(decompression.strict, 0),
          )

        ()
      }
    }

    for {
      resolvedHosts <- dnsResolver.resolve(location.host)
      ch            <-
        // Use Happy Eyeballs algorithm
        happyEyeballsConnect(
          resolvedHosts,
          channelFactory,
          eventLoopGroup,
          location,
          initializer,
          connectionTimeout,
          localAddress,
        )
    } yield ch
  }

  /**
   * Attempts to connect to a single address.
   */
  private def connectToAddress(
    host: InetAddress,
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    location: URL.Location.Absolute,
    initializer: ChannelInitializer[JChannel],
    connectionTimeout: Option[Duration],
    localAddress: Option[InetSocketAddress],
  )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] = {
    ZIO.suspend {
      val bootstrap = new Bootstrap()
        .channelFactory(channelFactory)
        .group(eventLoopGroup)
        .remoteAddress(new InetSocketAddress(host, location.port))
        .withOption[Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout.map(_.toMillis.toInt))
        .handler(initializer)
      localAddress.foreach(bootstrap.localAddress)

      val channelFuture = bootstrap.connect()
      val ch            = channelFuture.channel()
      Scope.addFinalizer {
        NettyFutureExecutor.executed {
          channelFuture.cancel(true)
          ch.close()
        }.whenDiscard(ch.isOpen).ignoreLogged
      }.uninterruptible *> NettyFutureExecutor.executed(channelFuture).as(ch)
    }
  }

  /**
   * Returns a sequence of connection attempts with their delays. Per RFC 8305,
   * we start with IPv6, then after firstAddressFamilyDelay we try IPv4, then
   * alternate between families.
   */
  private def sortAddresses(resolvedHosts: Chunk[InetAddress]): List[InetAddress] = {
    val (ipv6Addresses, ipv4Addresses) = resolvedHosts.partition(_.isInstanceOf[Inet6Address])
    val ipv6Iter                       = ipv6Addresses.iterator
    val ipv4Iter                       = ipv4Addresses.iterator
    val builder                        = List.newBuilder[InetAddress]
    builder.sizeHint(resolvedHosts.size)

    // Alternate between families
    var useIpv6 = true
    while (ipv6Iter.hasNext || ipv4Iter.hasNext) {

      if (useIpv6 && ipv6Iter.hasNext) {
        builder += ipv6Iter.next()
      } else if (ipv4Iter.hasNext) {
        builder += ipv4Iter.next()
      } else if (ipv6Iter.hasNext) {
        builder += ipv6Iter.next()
      }

      useIpv6 = !useIpv6
    }

    builder.result()
  }

  /**
   * Implements Happy Eyeballs (RFC 8305) connection algorithm. Races connection
   * attempts to IPv6 and IPv4 addresses with staggered delays.
   */
  private def happyEyeballsConnect(
    resolvedHosts: Chunk[InetAddress],
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    location: URL.Location.Absolute,
    initializer: ChannelInitializer[JChannel],
    connectionTimeout: Option[Duration],
    localAddress: Option[InetSocketAddress],
  )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] = {

    if (resolvedHosts.isEmpty) {
      ZIO.fail(new RuntimeException("No addresses to connect to"))
    } else if (resolvedHosts.size == 1) {
      connectToAddress(
        resolvedHosts.head,
        channelFactory,
        eventLoopGroup,
        location,
        initializer,
        connectionTimeout,
        localAddress,
      )
    } else {
      val addresses = sortAddresses(resolvedHosts)
      for {
        lastFailed <- Queue.dropping[Unit](requestedCapacity = 1)
        successful <- Ref.make(List.empty[JChannel])
        _          <- ZIO.raceAll(
          connectToAddress(
            addresses.head,
            channelFactory,
            eventLoopGroup,
            location,
            initializer,
            connectionTimeout,
            localAddress,
          ).onExit {
            case e: Exit.Success[JChannel] => successful.update(channels => channels :+ e.value)
            case _: Exit.Failure[_]        => lastFailed.offer(()).unit
          },
          addresses.tail.zipWithIndex.map { case (address, index) =>
            ZIO.sleep(HappyEyeballsDelay * index.toDouble).raceFirst(lastFailed.take).ignore *>
              connectToAddress(
                address,
                channelFactory,
                eventLoopGroup,
                location,
                initializer,
                connectionTimeout,
                localAddress,
              ).onExit {
                case e: Exit.Success[JChannel] => successful.update(channels => channels :+ e.value)
                case _: Exit.Failure[_]        => lastFailed.offer(()).unit
              }
          },
        )
        channels   <- successful.get
        channel    <- channels.collectFirst { case c if c.isOpen => c } match {
          case ch: Some[JChannel] => ZIO.succeed(ch.value)
          case None               => ZIO.fail(new RuntimeException("All connection attempts failed"))
        }
        _          <- ZIO.foreachDiscard(channels.tail)(ch => ZIO.ignore(ch.close()))
      } yield channel

    }
  }

  /**
   * Refreshes the idle timeout handler on the channel pipeline.
   * @return
   *   true if the handler was successfully refreshed prior to the channel being
   *   closed
   */
  private def refreshIdleTimeoutHandler(
    channel: JChannel,
    timeout: Duration,
  ): Boolean = {
    channel
      .pipeline()
      .replace(
        Names.ReadTimeoutHandler,
        Names.ReadTimeoutHandler,
        new ReadTimeoutHandler(timeout.toMillis, TimeUnit.MILLISECONDS),
      )
    channel.isOpen
  }

  private final class ReadTimeoutErrorHandler(nettyRuntime: NettyRuntime)(implicit trace: Trace)
      extends ChannelInboundHandlerAdapter {

    implicit private val unsafe: Unsafe = Unsafe.unsafe

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      cause match {
        case _: ReadTimeoutException => nettyRuntime.unsafeRunSync(ZIO.logDebug("ReadTimeoutException caught"))
        case _                       => super.exceptionCaught(ctx, cause)
      }
    }
  }

  private final class NoNettyConnectionPool(
    channelFactory: JChannelFactory[JChannel],
    eventLoopGroup: JEventLoopGroup,
    nettyRuntime: NettyRuntime,
    dnsResolver: DnsResolver,
  ) extends NettyConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxInitialLineLength: Int,
      maxHeaderSize: Int,
      decompression: Decompression,
      idleTimeout: Option[Duration],
      connectionTimeout: Option[Duration],
      localAddress: Option[InetSocketAddress] = None,
    )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] =
      createChannel(
        channelFactory,
        eventLoopGroup,
        nettyRuntime,
        location,
        proxy,
        sslOptions,
        maxInitialLineLength,
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
    maxInitialLineLength: Int,
    maxHeaderSize: Int,
    decompression: Decompression,
    idleTimeout: Option[Duration],
    connectionTimeout: Option[Duration],
  )

  private final class ZioNettyConnectionPool(
    pool: ZKeyedPool[Throwable, PoolKey, JChannel],
    maxItems: PoolKey => Int,
  ) extends NettyConnectionPool {
    override def get(
      location: Location.Absolute,
      proxy: Option[Proxy],
      sslOptions: ClientSSLConfig,
      maxInitialLineLength: Int,
      maxHeaderSize: Int,
      decompression: Decompression,
      idleTimeout: Option[Duration],
      connectionTimeout: Option[Duration],
      localAddress: Option[InetSocketAddress] = None,
    )(implicit trace: Trace): ZIO[Scope, Throwable, JChannel] = ZIO.uninterruptibleMask { restore =>
      val key = PoolKey(
        location,
        proxy,
        sslOptions,
        maxInitialLineLength,
        maxHeaderSize,
        decompression,
        idleTimeout,
        connectionTimeout,
      )

      restore(pool.get(key)).withEarlyRelease.flatMap { case (release, channel) =>
        // Channel might have closed while in the pool, either because of a timeout or because of a connection error
        // We retry a few times hoping to obtain an open channel
        // NOTE: We need to release the channel before retrying, so that it can be closed and removed from the pool
        // We do that in a forked fiber so that we don't "block" the current fiber while the new resource is obtained
        if (channel.isOpen && idleTimeout.fold(true)(refreshIdleTimeoutHandler(channel, _)))
          ZIO.succeed(channel)
        else
          invalidate(channel) *> release.forkDaemon *> ZIO.fail(None)
      }
        .retry(retrySchedule(key))
        .catchAll {
          case None         => pool.get(key) // We did all we could, let the caller handle it
          case e: Throwable => ZIO.fail(e)
        }
        .withFinalizer(c => ZIO.unless(c.isOpen)(invalidate(c)))
    }

    override def invalidate(channel: JChannel)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      pool.invalidate(channel)

    override def enableKeepAlive: Boolean = true

    private def retrySchedule[E](key: PoolKey)(implicit trace: Trace) =
      Schedule.recurWhile[E] {
        case None => true
        case _    => false
      } && Schedule.recurs(maxItems(key))
  }

  def fromConfig(
    config: ConnectionPoolConfig,
  )(implicit
    trace: Trace,
  ): ZIO[Scope with NettyClientDriver with DnsResolver, Nothing, NettyConnectionPool] =
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
    } yield new NoNettyConnectionPool(driver.channelFactory, driver.eventLoopGroup, driver.nettyRuntime, dnsResolver)

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
      poolFn = (key: PoolKey) =>
        createChannel(
          driver.channelFactory,
          driver.eventLoopGroup,
          driver.nettyRuntime,
          key.location,
          key.proxy,
          key.sslOptions,
          key.maxInitialLineLength,
          key.maxHeaderSize,
          key.decompression,
          key.idleTimeout,
          key.connectionTimeout,
          None,
          dnsResolver,
        ).uninterruptible
      _size  = (key: PoolKey) => size(key.location)
      keyedPool <- ZKeyedPool.make(poolFn, _size)
    } yield new ZioNettyConnectionPool(keyedPool, _size)

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
      poolFn = (key: PoolKey) =>
        createChannel(
          driver.channelFactory,
          driver.eventLoopGroup,
          driver.nettyRuntime,
          key.location,
          key.proxy,
          key.sslOptions,
          key.maxInitialLineLength,
          key.maxHeaderSize,
          key.decompression,
          key.idleTimeout,
          key.connectionTimeout,
          None,
          dnsResolver,
        ).uninterruptible
      keyedPool <- ZKeyedPool.make(
        poolFn,
        (key: PoolKey) => min(key.location) to max(key.location),
        (key: PoolKey) => ttl(key.location),
      )
    } yield new ZioNettyConnectionPool(keyedPool, key => max(key.location))

  implicit final class BootstrapSyntax(val bootstrap: Bootstrap) extends AnyVal {
    def withOption[T](option: ChannelOption[T], value: Option[T]): Bootstrap =
      value match {
        case Some(value) => bootstrap.option(option, value)
        case None        => bootstrap
      }
  }
}
