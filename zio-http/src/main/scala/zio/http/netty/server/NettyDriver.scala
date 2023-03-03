package zio.http.netty.server

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import zio._
import zio.http.{App, ClientConfig, ClientDriver, Driver, Http, Server, ServerConfig} // scalafix:ok;
import zio.http.netty._
import zio.http.netty.client.NettyClientDriver
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.util.ResourceLeakDetector

private[zio] final case class NettyDriver(
  appRef: AppRef,
  channelFactory: ChannelFactory[ServerChannel],
  channelInitializer: ChannelInitializer[Channel],
  serverInboundHandler: ServerInboundHandler,
  eventLoopGroup: EventLoopGroup,
  errorCallbackRef: ErrorCallbackRef,
  serverConfig: ServerConfig,
  nettyServerConfig: NettyServerConfig,
) extends Driver { self =>

  def start(implicit trace: Trace): RIO[Scope, Int] =
    for {
      serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
      chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
      _               <- NettyFutureExecutor.scoped(chf)
      _               <- ZIO.succeed(ResourceLeakDetector.setLevel(nettyServerConfig.leakDetectionLevel.toNetty))
      port            <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield port

  def setErrorCallback(newCallback: Option[Server.ErrorCallback])(implicit trace: Trace): UIO[Unit] = ZIO.succeed {
    var loop = true
    while (loop) {
      val oldCallback = errorCallbackRef.get()
      if (errorCallbackRef.compareAndSet(oldCallback, newCallback)) loop = false
    }
  }

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

  override def createClientDriver(config: ClientConfig)(implicit trace: Trace): ZIO[Scope, Throwable, ClientDriver] =
    for {
      channelFactory <- ChannelFactories.Client.fromConfig.build
        .provideSomeEnvironment[Scope](_ ++ ZEnvironment[ChannelType.Config](config))
      nettyRuntime   <- NettyRuntime.default.build
    } yield NettyClientDriver(channelFactory.get, eventLoopGroup, nettyRuntime.get, config)

  override def toString: String = s"NettyDriver($serverConfig)"
}

private[zio] object NettyDriver {

  implicit val trace: Trace = Trace.empty

  val make: ZIO[
    AppRef
      & ChannelFactory[ServerChannel]
      & ChannelInitializer[Channel]
      & EventLoopGroup
      & ErrorCallbackRef
      & ServerConfig
      & NettyServerConfig
      & ServerInboundHandler,
    Nothing,
    Driver,
  ] =
    for {
      app   <- ZIO.service[AppRef]
      cf    <- ZIO.service[ChannelFactory[ServerChannel]]
      cInit <- ZIO.service[ChannelInitializer[Channel]]
      elg   <- ZIO.service[EventLoopGroup]
      ecb   <- ZIO.service[ErrorCallbackRef]
      sc    <- ZIO.service[ServerConfig]
      nsc   <- ZIO.service[NettyServerConfig]
      sih   <- ZIO.service[ServerInboundHandler]
    } yield new NettyDriver(
      appRef = app,
      channelFactory = cf,
      channelInitializer = cInit,
      serverInboundHandler = sih,
      eventLoopGroup = elg,
      errorCallbackRef = ecb,
      serverConfig = sc,
      nettyServerConfig = nsc,
    )

  val manual
    : ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & ServerConfig & NettyServerConfig, Nothing, Driver] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.makeSome[EventLoopGroup & ChannelFactory[ServerChannel] & ServerConfig & NettyServerConfig, Driver](
      ZLayer.succeed(
        new AtomicReference[(App[Any], ZEnvironment[Any])]((Http.empty, ZEnvironment.empty)),
      ),
      ZLayer.succeed(new AtomicReference[Option[Server.ErrorCallback]](Option.empty)),
      ZLayer.succeed(ServerTime.make(1000.millis)),
      NettyRuntime.default,
      ServerChannelInitializer.layer,
      ServerInboundHandler.layer,
      ZLayer(make),
    )
  }

  val customized: ZLayer[ServerConfig & NettyServerConfig, Throwable, Driver] = {
    val serverChannelFactory: ZLayer[ServerConfig, Nothing, ChannelFactory[ServerChannel]] =
      ChannelFactories.Server.fromConfig
    val eventLoopGroup: ZLayer[ServerConfig, Nothing, EventLoopGroup]                      = EventLoopGroups.fromConfig

    ZLayer.makeSome[ServerConfig & NettyServerConfig, Driver](
      eventLoopGroup,
      serverChannelFactory,
      manual,
    )
  }

  val default: ZLayer[ServerConfig, Throwable, Driver] =
    ZLayer.makeSome[ServerConfig, Driver](
      NettyServerConfig.live,
      customized,
    )
}
