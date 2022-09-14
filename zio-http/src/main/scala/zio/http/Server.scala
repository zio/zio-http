package zio.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.util.ResourceLeakDetector
import zio._
import zio.http.netty.ChannelType
import zio.http.service._

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

trait Server {
  def install[R](httpApp: HttpApp[R, Throwable]): URIO[R, Unit]

  def port[R]: URIO[R, Int]
}

object Server {
  def serve[R](httpApp: HttpApp[R, Throwable]): URIO[R with Server, Unit] =
    install(httpApp) *> ZIO.never

  def install[R](httpApp: HttpApp[R, Throwable]): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install(httpApp)) *> ZIO.serviceWithZIO[Server](_.port)
  }

  val default = ServerConfigLayer.default >>> live

  val live: ZLayer[ServerConfig, Throwable, Server] = ZLayer.scoped {
    for {
      settings       <- ZIO.service[ServerConfig]
      channelFactory <- channelFactory(settings)
      eventLoopGroup <- eventLoopGroup(settings)
      rtm            <- HttpRuntime.sticky[Any](eventLoopGroup)
      time   = ServerTime.make(1000 millis)
      appRef = new AtomicReference[HttpApp[Any, Throwable]](Http.empty)
      reqHandler <- ZIO.succeed(ServerInboundHandler(appRef, rtm, settings, time))
      init            = ServerChannelInitializer(rtm, settings, reqHandler)
      serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      chf  <- ZIO.attempt(serverBootstrap.childHandler(init).bind(settings.address))
      _    <- ChannelFuture.scoped(chf)
      _    <- ZIO.succeed(ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel))
      port <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield ServerLive(appRef, port)
  }

  val base: ZIO[Scope with Driver with AtomicReference[HttpApp[Any, Throwable]], Throwable, Server] =
    for {
      driver <- ZIO.service[Driver]
      appRef <- ZIO.service[AtomicReference[HttpApp[Any, Throwable]]]
      port   <- driver.start()
    } yield ServerLive(appRef, port)

  val nettyLive: ZLayer[ServerConfig, Throwable, Server] = {
    val appRef = ZLayer.succeed(new AtomicReference[HttpApp[Any, Throwable]](Http.empty))
    val time   = ZLayer.succeed(ServerTime.make(1000 millis))
    ZLayer.scoped {
      base.provideSome[ServerConfig & Scope](
        netty.NettyDriver.layer,
        appRef,
        time,
        netty.ServerChannelInitializer.layer,
        netty.ServerInboundHandler.layer,
        netty.Channels.Server.fromConfig,
        netty.EventLoopGroups.fromConfig,
        netty.NettyRuntime.usingSharedThreadPool,
      )
    }
  }

  private def channelFactory(config: ServerConfig): UIO[ServerChannelFactory] = {
    config.channelType match {
      case ChannelType.NIO    => ServerChannelFactory.nio
      case ChannelType.EPOLL  => ServerChannelFactory.epoll
      case ChannelType.KQUEUE => ServerChannelFactory.kQueue
      case ChannelType.URING  => ServerChannelFactory.uring
      case ChannelType.AUTO   => ServerChannelFactory.auto
    }
  }

  private def eventLoopGroup(config: ServerConfig): ZIO[Scope, Nothing, EventLoopGroup] = {
    config.channelType match {
      case ChannelType.NIO    => EventLoopGroup.Live.nio(config.nThreads)
      case ChannelType.EPOLL  => EventLoopGroup.Live.epoll(config.nThreads)
      case ChannelType.KQUEUE => EventLoopGroup.Live.kQueue(config.nThreads)
      case ChannelType.URING  => EventLoopGroup.Live.uring(config.nThreads)
      case ChannelType.AUTO   => EventLoopGroup.Live.auto(config.nThreads)
    }
  }

  val test = ServerConfigLayer.testServerConfig >>> nettyLive

  private final case class ServerLive(
    appRef: java.util.concurrent.atomic.AtomicReference[HttpApp[Any, Throwable]],
    bindPort: Int,
  ) extends Server {
    override def install[R](httpApp: HttpApp[R, Throwable]): URIO[R, Unit] =
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
      }
    override def port[R]: URIO[R, Int]                                     = ZIO.succeed(bindPort)
  }

}
