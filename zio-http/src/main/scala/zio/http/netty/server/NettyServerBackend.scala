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

import zio.http.netty._
import zio.http.netty.client.NettyClientBackend
import zio.http.{App, ClientBackend, Http, Server, ServerBackend}

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.util.ResourceLeakDetector

private[zio] final case class NettyServerBackend(
  appRef: AppRef,
  channelFactory: ChannelFactory[ServerChannel],
  channelInitializer: ChannelInitializer[Channel],
  serverInboundHandler: ServerInboundHandler,
  eventLoopGroup: EventLoopGroup,
  serverConfig: Server.Config,
  nettyConfig: NettyConfig,
) extends ServerBackend { self =>

  def start(implicit trace: Trace): RIO[Scope, Int] =
    for {
      serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
      chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
      _               <- NettyFutureExecutor.scoped(chf)
      _               <- ZIO.succeed(ResourceLeakDetector.setLevel(nettyConfig.leakDetectionLevel.toNetty))
      port            <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield port

  def addApp[R](newApp: App[R], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit] = ZIO.succeed {
    var loop = true
    while (loop) {
      val oldAppAndEnv     = appRef.get()
      val (oldApp, oldEnv) = oldAppAndEnv
      val updatedApp       = (oldApp ++ newApp).asInstanceOf[App[Any]]
      val updatedEnv       = oldEnv.unionAll(env)
      val updatedAppAndEnv = (updatedApp, updatedEnv)

      if (appRef.compareAndSet(oldAppAndEnv, updatedAppAndEnv)) loop = false
    }
    serverInboundHandler.refreshApp()
  }

  override def createClientBackend()(implicit trace: Trace): ZIO[Scope, Throwable, ClientBackend] =
    for {
      channelFactory <- ChannelFactories.Client.live.build
        .provideSomeEnvironment[Scope](_ ++ ZEnvironment[ChannelType.Config](nettyConfig))
      nettyRuntime   <- NettyRuntime.live.build
    } yield NettyClientBackend(channelFactory.get, eventLoopGroup, nettyRuntime.get, nettyConfig)

  override def toString: String = s"NettyServerBackend($serverConfig)"
}

private[zio] object NettyServerBackend {

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
    ServerBackend,
  ] =
    for {
      app   <- ZIO.service[AppRef]
      cf    <- ZIO.service[ChannelFactory[ServerChannel]]
      cInit <- ZIO.service[ChannelInitializer[Channel]]
      elg   <- ZIO.service[EventLoopGroup]
      sc    <- ZIO.service[Server.Config]
      nsc   <- ZIO.service[NettyConfig]
      sih   <- ZIO.service[ServerInboundHandler]
    } yield new NettyServerBackend(
      appRef = app,
      channelFactory = cf,
      channelInitializer = cInit,
      serverInboundHandler = sih,
      eventLoopGroup = elg,
      serverConfig = sc,
      nettyConfig = nsc,
    )

  val manual
    : ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & Server.Config & NettyConfig, Nothing, ServerBackend] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.makeSome[EventLoopGroup & ChannelFactory[ServerChannel] & Server.Config & NettyConfig, ServerBackend](
      ZLayer.succeed(
        new AtomicReference[(App[Any], ZEnvironment[Any])]((Http.empty, ZEnvironment.empty)),
      ),
      ZLayer.succeed(ServerTime.make(1000.millis)),
      NettyRuntime.live,
      ServerChannelInitializer.layer,
      ServerInboundHandler.live,
      ZLayer(make),
    )
  }

  val customized: ZLayer[Server.Config & NettyConfig, Throwable, ServerBackend] = {
    val serverChannelFactory: ZLayer[NettyConfig, Nothing, ChannelFactory[ServerChannel]] =
      ChannelFactories.Server.fromConfig
    val eventLoopGroup: ZLayer[NettyConfig, Nothing, EventLoopGroup]                      = EventLoopGroups.live

    ZLayer.makeSome[Server.Config & NettyConfig, ServerBackend](
      eventLoopGroup,
      serverChannelFactory,
      manual,
    )
  }

  val live: ZLayer[Server.Config, Throwable, ServerBackend] =
    ZLayer.makeSome[Server.Config, ServerBackend](
      ZLayer.succeed(NettyConfig.default),
      customized,
    )
}
