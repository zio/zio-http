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
import zio.http.netty.{Names, NettyFutureExecutor, NettyProxy, NettyRuntime}

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel => JChannel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, _}
import io.netty.handler.codec.http.{HttpClientCodec, HttpContentDecompressor}
import io.netty.handler.proxy.HttpProxyHandler
import io.netty.handler.timeout.{ReadTimeoutException, ReadTimeoutHandler}

private[netty] trait NettyConnectionPool extends ConnectionPool[JChannel]

private[netty] object NettyConnectionPool {

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

        // Add read timeout handler if configured
        // This provides channel-level timeout detection (triggers if no bytes are read)
        // Works in coordination with body-level timeout in AsyncBodyReader:
        // - Channel-level: Detects complete channel stalls (no bytes at all)
        // - Body-level: Detects body reading stalls (headers received but body incomplete)
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
      hosts         <- Random.shuffle(resolvedHosts.toList)
      hostsNec      <- ZIO.succeed(NonEmptyChunk.fromIterable(hosts.head, hosts.tail))
      ch            <- collectFirstSuccess(hostsNec) { host =>
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
            }.when(ch.isOpen).ignoreLogged
          } *> NettyFutureExecutor.executed(channelFuture).as(ch)
        }
      }
    } yield ch
  }

  private def collectFirstSuccess[R, E, A, B](
    as: NonEmptyChunk[A],
  )(f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, B] = {
    ZIO.suspendSucceed {
      val it                 = as.iterator
      def loop: ZIO[R, E, B] = f(it.next()).catchAll(e => if (it.hasNext) loop else ZIO.fail(e))
      loop
    }
  }

  /**
   * Refreshes the idle timeout handler on the channel pipeline when reusing a
   * connection from the pool. This ensures the timeout counter resets for each
   * new request, preventing timeouts from accumulating across requests.
   *
   * The ReadTimeoutHandler monitors channel-level read activity and triggers if
   * no bytes are received within the timeout period. This works together with
   * AsyncBodyReader's body-specific timeout to provide comprehensive timeout
   * coverage:
   *   - Channel timeout: Detects complete stalls (no data at all)
   *   - Body timeout: Detects incomplete body reads (headers sent but body
   *     hangs)
   *
   * @param channel
   *   The channel to refresh the timeout handler on
   * @param timeout
   *   The timeout duration to apply
   * @return
   *   true if the handler was successfully refreshed prior to the channel being
   *   closed, false otherwise
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

  /**
   * Handler for ReadTimeoutExceptions from Netty's ReadTimeoutHandler.
   *
   * This provides the first layer of timeout protection at the channel level.
   * When a ReadTimeoutException occurs (no bytes read within idleTimeout):
   *   1. Logs the event for debugging
   *   2. Closes the channel to trigger cleanup
   *   3. Allows AsyncBodyReader to detect the closure and fail appropriately
   *
   * This works in coordination with AsyncBodyReader's body-specific timeout:
   *   - Channel-level (this handler): Triggers if channel becomes completely
   *     idle
   *   - Body-level (AsyncBodyReader): Triggers if body reading stalls
   *     specifically
   *
   * The dual-layer approach ensures timeout detection even if one layer fails.
   */
  private final class ReadTimeoutErrorHandler(nettyRuntime: NettyRuntime)(implicit trace: Trace)
      extends ChannelInboundHandlerAdapter {

    implicit private val unsafe: Unsafe = Unsafe.unsafe

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      cause match {
        case _: ReadTimeoutException =>
          // Log the timeout at debug level
          nettyRuntime.unsafeRunSync(ZIO.logDebug(s"ReadTimeoutException on channel ${ctx.channel()}"))
          // Close the channel to ensure proper cleanup
          // The timeout will be detected by response handlers (AsyncBodyReader)
          ctx.close(): Unit
        case _                       =>
          super.exceptionCaught(ctx, cause)
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
