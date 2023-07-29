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
import zio.http.{ClientDriver, Driver, HttpApp, Server}

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

  def start(implicit trace: zio.http.Trace): RIO[Scope, StartResult] =
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

  def addApp[R](newApp: HttpApp[R], env: ZEnvironment[R])(implicit trace: zio.http.Trace): UIO[Unit] = ZIO.succeed {
    var loop = true
    while (loop) {
      val oldAppAndEnv     = appRef.get()
      val (oldApp, oldEnv) = oldAppAndEnv
      val updatedApp       = (oldApp ++ newApp).asInstanceOf[HttpApp[Any]]
      val updatedEnv       = oldEnv.unionAll(env)
      val updatedAppAndEnv = (updatedApp, updatedEnv)

      if (appRef.compareAndSet(oldAppAndEnv, updatedAppAndEnv)) loop = false
    }
    serverInboundHandler.refreshApp()
  }

  override def createClientDriver()(implicit trace: zio.http.Trace): ZIO[Scope, Throwable, ClientDriver] =
    for {
      channelFactory <- ChannelFactories.Client.live.build
        .provideSomeEnvironment[Scope](_ ++ ZEnvironment[ChannelType.Config](nettyConfig))
      nettyRuntime   <- NettyRuntime.live.build
    } yield NettyClientDriver(channelFactory.get, eventLoopGroup, nettyRuntime.get, nettyConfig)

  override def toString: String = s"NettyDriver($serverConfig)"
}

private[zio] object NettyDriver {

  implicit val trace: zio.http.Trace = zio.http.Trace.empty

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
    implicit val trace: zio.http.Trace = zio.http.Trace.empty
    ZLayer.makeSome[EventLoopGroup & ChannelFactory[ServerChannel] & Server.Config & NettyConfig, Driver](
      ZLayer.succeed(
        new AtomicReference[(HttpApp[Any], ZEnvironment[Any])]((HttpApp.empty, ZEnvironment.empty)),
      ),
      ZLayer.succeed(ServerTime.make(1000.millis)),
      NettyRuntime.live,
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
