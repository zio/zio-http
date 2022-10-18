package zio.http.netty.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.util.ResourceLeakDetector
import zio._
import zio.http.netty._
import zio.http.service.ServerTime
import zio.http.{Driver, Http, HttpApp, Server, ServerConfig}

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] final case class NettyDriver(
  appRef: AppRef,
  channelFactory: ChannelFactory[ServerChannel],
  channelInitializer: ChannelInitializer[Channel],
  eventLoopGroup: EventLoopGroup,
  errorCallbackRef: ErrorCallbackRef,
  serverConfig: ServerConfig,
) extends Driver { self =>

  def start(implicit trace: Trace): RIO[Scope, Int] =
    for {
      serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
      chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
      _               <- NettyFutureExecutor.scoped(chf)
      _    <- ZIO.succeed(ResourceLeakDetector.setLevel(serverConfig.leakDetectionLevel.jResourceLeakDetectionLevel))
      port <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield port

  def setErrorCallback(newCallback: Option[Server.ErrorCallback])(implicit trace: Trace): UIO[Unit] = ZIO.succeed {
    var loop = true
    while (loop) {
      val oldCallback = errorCallbackRef.get()
      if (errorCallbackRef.compareAndSet(oldCallback, newCallback)) loop = false
    }
  }

  def addApp[R](newApp: HttpApp[R, Throwable], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit] = ZIO.succeed {
    var loop = true
    while (loop) {
      val oldAppAndEnv     = appRef.get()
      val (oldApp, oldEnv) = oldAppAndEnv
      val updatedApp       = (oldApp ++ newApp).asInstanceOf[HttpApp[Any, Throwable]]
      val updatedEnv       = oldEnv.unionAll(env)
      val updatedAppAndEnv = (updatedApp, updatedEnv)

      if (appRef.compareAndSet(oldAppAndEnv, updatedAppAndEnv)) loop = false
    }
  }

}

object NettyDriver {

  implicit val trace: Trace = Trace.empty

  private type Env = AppRef
    with ChannelFactory[ServerChannel]
    with ChannelInitializer[Channel]
    with EventLoopGroup
    with ErrorCallbackRef
    with ServerConfig

  val make: ZIO[Env, Nothing, Driver] =
    for {
      app   <- ZIO.service[AppRef]
      cf    <- ZIO.service[ChannelFactory[ServerChannel]]
      cInit <- ZIO.service[ChannelInitializer[Channel]]
      elg   <- ZIO.service[EventLoopGroup]
      ecb   <- ZIO.service[ErrorCallbackRef]
      sc    <- ZIO.service[ServerConfig]
    } yield new NettyDriver(
      appRef = app,
      channelFactory = cf,
      channelInitializer = cInit,
      eventLoopGroup = elg,
      errorCallbackRef = ecb,
      serverConfig = sc,
    )

  val default: ZLayer[ServerConfig, Throwable, Driver] = ZLayer.scoped {
    // val app  = ZLayer.succeed(new AtomicReference[HttpApp[Any, Throwable]](Http.empty))
    val app  = ZLayer.succeed(new AtomicReference[(HttpApp[Any, Throwable], ZEnvironment[Any])]((Http.empty, ZEnvironment.empty)))
    val ecb  = ZLayer.succeed(new AtomicReference[Option[Server.ErrorCallback]](Option.empty))
    val time = ZLayer.succeed(ServerTime.make(1000 millis))

    val serverChannelFactory: ZLayer[ServerConfig, Nothing, ChannelFactory[ServerChannel]] =
      ChannelFactories.Server.fromConfig
    val eventLoopGroup: ZLayer[Scope with ServerConfig, Nothing, EventLoopGroup]           = EventLoopGroups.fromConfig
    val nettyRuntime: ZLayer[EventLoopGroup, Nothing, NettyRuntime] = NettyRuntime.usingSharedThreadPool
    val serverChannelInitializer: ZLayer[ServerInboundHandler with ServerConfig, Nothing, ServerChannelInitializer] =
      ServerChannelInitializer.layer
    val serverInboundHandler: ZLayer[
      ServerTime with ServerConfig with NettyRuntime with ErrorCallbackRef with AppRef,
      Nothing,
      ServerInboundHandler,
    ] = ServerInboundHandler.layer

    val serverLayers = app ++
      serverChannelFactory ++
      (
        (
          (time ++ app ++ ecb) ++
            (eventLoopGroup >>> nettyRuntime) >>> serverInboundHandler
        ) >>> serverChannelInitializer
      ) ++
      ecb ++
      eventLoopGroup

    make
      .provideSomeLayer[ServerConfig & Scope](
        serverLayers,
      )

  }

}
