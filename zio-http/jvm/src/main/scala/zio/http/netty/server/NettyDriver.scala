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

package zio.http.netty.server

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

import zio._

import zio.http.Driver.StartResult
import zio.http.netty._
import zio.http.netty.client.NettyClientDriver
import zio.http.{ClientDriver, Driver, Response, Routes, Server}

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.util.ResourceLeakDetector
private[zio] final case class NettyDriver(
  appRef: AppRef,
  channelFactory: ChannelFactory[ServerChannel],
  channelInitializer: ChannelInitializer[Channel],
  serverInboundHandler: ServerInboundHandler,
  eventLoopGroup: EventLoopGroup,
  serverConfig: Server.Config,
  nettyConfig: NettyConfig,
) extends Driver { self =>

  def start(implicit trace: Trace): RIO[Scope, StartResult] =
    for {
      serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
      chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
      _               <- NettyFutureExecutor.scoped(chf)
      _               <- ZIO.succeed(ResourceLeakDetector.setLevel(nettyConfig.leakDetectionLevel.toNetty))
      channel         <- ZIO.attempt(chf.channel())
      port            <- ZIO.attempt(channel.localAddress().asInstanceOf[InetSocketAddress].getPort)

      _ <- Scope.addFinalizer(
        NettyFutureExecutor.executed(channel.close()).ignoreLogged,
      )
    } yield StartResult(port, serverInboundHandler.inFlightRequests)

  def addApp[R](newApp: Routes[R, Response], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit] =
    ZIO.fiberId.map { fiberId =>
      var loop = true
      while (loop) {
        val oldAppAndRt     = appRef.get()
        val (oldApp, oldRt) = oldAppAndRt
        val updatedApp      = (oldApp ++ newApp).asInstanceOf[Routes[Any, Response]]
        val updatedEnv      = oldRt.environment.unionAll(env)
        // Update the fiberRefs with the new environment to avoid doing this every time we run / fork a fiber
        val updatedFibRefs  = oldRt.fiberRefs.updatedAs(fiberId)(FiberRef.currentEnvironment, updatedEnv)
        val updatedRt       = Runtime(updatedEnv, updatedFibRefs, oldRt.runtimeFlags)
        val updatedAppAndRt = (updatedApp, updatedRt)

        if (appRef.compareAndSet(oldAppAndRt, updatedAppAndRt)) loop = false
      }
      serverInboundHandler.refreshApp()
    }

  override def createClientDriver()(implicit trace: Trace): ZIO[Scope, Throwable, ClientDriver] =
    for {
      channelFactory <- ChannelFactories.Client.live.build
        .provideSomeEnvironment[Scope](_ ++ ZEnvironment[ChannelType.Config](nettyConfig))
      nettyRuntime   <- NettyRuntime.live.build
    } yield NettyClientDriver(channelFactory.get, eventLoopGroup, nettyRuntime.get, nettyConfig)

  override def toString: String = s"NettyDriver($serverConfig)"
}

object NettyDriver {

  implicit val trace: Trace = Trace.empty

  val make: ZIO[
    AppRef
      & ChannelFactory[ServerChannel]
      & ChannelInitializer[Channel]
      & EventLoopGroup
      & Server.Config
      & NettyConfig
      & ServerInboundHandler,
    Nothing,
    Driver,
  ] =
    for {
      app   <- ZIO.service[AppRef]
      cf    <- ZIO.service[ChannelFactory[ServerChannel]]
      cInit <- ZIO.service[ChannelInitializer[Channel]]
      elg   <- ZIO.service[EventLoopGroup]
      sc    <- ZIO.service[Server.Config]
      nsc   <- ZIO.service[NettyConfig]
      sih   <- ZIO.service[ServerInboundHandler]
    } yield new NettyDriver(
      appRef = app,
      channelFactory = cf,
      channelInitializer = cInit,
      serverInboundHandler = sih,
      eventLoopGroup = elg,
      serverConfig = sc,
      nettyConfig = nsc,
    )

  val manual: ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & Server.Config & NettyConfig, Nothing, Driver] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.makeSome[EventLoopGroup & ChannelFactory[ServerChannel] & Server.Config & NettyConfig, Driver](
      ZLayer(AppRef.empty),
      ServerChannelInitializer.layer,
      ServerInboundHandler.live,
      ZLayer(make),
    )
  }

  val customized: ZLayer[Server.Config & NettyConfig, Throwable, Driver] = {
    val serverChannelFactory: ZLayer[NettyConfig, Nothing, ChannelFactory[ServerChannel]] =
      ChannelFactories.Server.fromConfig
    val eventLoopGroup: ZLayer[NettyConfig, Nothing, EventLoopGroup]                      = EventLoopGroups.live

    ZLayer.makeSome[Server.Config & NettyConfig, Driver](
      eventLoopGroup,
      serverChannelFactory,
      manual,
    )
  }

  val live: ZLayer[Server.Config, Throwable, Driver] =
    ZLayer.makeSome[Server.Config, Driver](
      ZLayer.succeed(NettyConfig.default),
      customized,
    )
}
