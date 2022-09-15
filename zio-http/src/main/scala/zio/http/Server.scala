package zio.http

// import io.netty.bootstrap.ServerBootstrap
// import io.netty.util.ResourceLeakDetector
import zio._
import zio.http.Server.ErrorCallback
import zio.http.netty.server._

import java.util.concurrent.atomic.AtomicReference
// import netty.ChannelType

trait Server {
  def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback] = None): URIO[R, Unit]

  def port: Int

}

object Server {

  type ErrorCallback = Throwable => ZIO[Any, Nothing, Unit]
  def serve[R](
    httpApp: HttpApp[R, Throwable],
    errorCallback: Option[ErrorCallback] = None,
  ): URIO[R with Server, Nothing] =
    install(httpApp, errorCallback) *> ZIO.never

  def install[R](
    httpApp: HttpApp[R, Throwable],
    errorCallback: Option[ErrorCallback] = None,
  ): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install(httpApp, errorCallback)) *> ZIO.service[Server].map(_.port)
  }

  val default = ServerConfig.live >>> live

  val live = NettyDriver.default >>> base

  // val oldLive: ZLayer[ServerConfig, Throwable, Server] = ZLayer.scoped {
  //   for {
  //     settings       <- ZIO.service[ServerConfig]
  //     channelFactory <- channelFactory(settings)
  //     eventLoopGroup <- eventLoopGroup(settings)
  //     rtm            <- HttpRuntime.sticky[Any](eventLoopGroup)
  //     time     = ServerTime.make(1000 millis)
  //     appRef   = new AtomicReference[HttpApp[Any, Throwable]](Http.empty)
  //     errorRef = new AtomicReference[Option[ErrorCallback]](None)
  //     reqHandler <- ZIO.succeed(ServerInboundHandler(appRef, rtm, settings, time, errorRef))
  //     init            = ServerChannelInitializer(rtm, settings, reqHandler)
  //     serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
  //     chf  <- ZIO.attempt(serverBootstrap.childHandler(init).bind(settings.address))
  //     _    <- ChannelFuture.scoped(chf)
  //     _    <- ZIO.succeed(ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel))
  //     port <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
  //   } yield ServerLive(appRef, errorRef, port)
  // }

  val base: ZLayer[
    Driver with AtomicReference[HttpApp[Any, Throwable]] with AtomicReference[Option[ErrorCallback]],
    Throwable,
    Server,
  ] = ZLayer.scoped {
    for {
      driver   <- ZIO.service[Driver]
      appRef   <- ZIO.service[AtomicReference[HttpApp[Any, Throwable]]]
      errorRef <- ZIO.service[AtomicReference[Option[ErrorCallback]]]
      port     <- driver.start()
    } yield ServerLive(appRef, errorRef, port)
  }

  // val live: ZLayer[ServerConfig, Throwable, Server] = {
  //   val appRef                  = ZLayer.succeed(new AtomicReference[HttpApp[Any, Throwable]](Http.empty))
  //   val errorRef                = ZLayer.succeed(new AtomicReference[Option[ErrorCallback]](None))
  //   val time                    = ZLayer.succeed(ServerTime.make(1000 millis))
  //   ZLayer.scoped {
  //     base.provideSome[ServerConfig & Scope](
  //       netty.NettyDriver.layer,
  //       appRef,
  //       errorRef,
  //       time,
  //       netty.ServerChannelInitializer.layer,
  //       netty.ServerInboundHandler.layer,
  //       netty.ChannelFactories.Server.fromConfig,
  //       netty.EventLoopGroups.fromConfig,
  //       netty.NettyRuntime.usingSharedThreadPool,
  //     )
  //   }
  // }

  // private def channelFactory(config: ServerConfig): UIO[ServerChannelFactory] = {
  //   config.channelType match {
  //     case ChannelType.NIO    => ServerChannelFactory.nio
  //     case ChannelType.EPOLL  => ServerChannelFactory.epoll
  //     case ChannelType.KQUEUE => ServerChannelFactory.kQueue
  //     case ChannelType.URING  => ServerChannelFactory.uring
  //     case ChannelType.AUTO   => ServerChannelFactory.auto
  //   }
  // }

  // private def eventLoopGroup(config: ServerConfig): ZIO[Scope, Nothing, EventLoopGroup] = {
  //   config.channelType match {
  //     case ChannelType.NIO    => EventLoopGroup.Live.nio(config.nThreads)
  //     case ChannelType.EPOLL  => EventLoopGroup.Live.epoll(config.nThreads)
  //     case ChannelType.KQUEUE => EventLoopGroup.Live.kQueue(config.nThreads)
  //     case ChannelType.URING  => EventLoopGroup.Live.uring(config.nThreads)
  //     case ChannelType.AUTO   => EventLoopGroup.Live.auto(config.nThreads)
  //   }
  // }

  private final case class ServerLive(
    appRef: java.util.concurrent.atomic.AtomicReference[HttpApp[Any, Throwable]],
    errorRef: java.util.concurrent.atomic.AtomicReference[Option[ErrorCallback]],
    bindPort: Int,
  ) extends Server {
    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] =
      ZIO.environment[R].map { env =>
        val newApp =
          if (env == ZEnvironment.empty) httpApp.asInstanceOf[HttpApp[Any, Throwable]]
          else httpApp.provideEnvironment(env)
        var loop   = true
        while (loop) {
          val oldApp = appRef.get()
          if (appRef.compareAndSet(oldApp, newApp ++ oldApp)) loop = false
        }
        ()
      } *> setErrorCallback(errorCallback)

    override def port: Int = bindPort

    private def setErrorCallback(errorCallback: Option[ErrorCallback]): UIO[Unit] = {
      ZIO
        .environment[Any]
        .as {
          var loop = true
          while (loop) {
            val oldErrorCallback = errorRef.get()
            if (errorRef.compareAndSet(oldErrorCallback, errorCallback)) loop = false
          }
          ()
        }
        .unless(errorCallback.isEmpty)
        .map(_.getOrElse(()))
    }
  }

}
