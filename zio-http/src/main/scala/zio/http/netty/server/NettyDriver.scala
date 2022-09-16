package zio.http.netty.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.util.ResourceLeakDetector
import zio._

import java.net.InetSocketAddress
import zio.http.{Driver, ServerConfig}
import zio.http.service.ServerTime
import zio.http.netty._

private[zio] final case class NettyDriver(
  channelFactory: ChannelFactory[ServerChannel],
  channelInitializer: ChannelInitializer[Channel],
  eventLoopGroup: EventLoopGroup,
  serverConfig: ServerConfig,
) extends Driver { self =>

  def start(): RIO[Scope, Int] =
    for {
      serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
      chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
      _               <- NettyFutureExecutor.scoped(chf)
      _    <- ZIO.succeed(ResourceLeakDetector.setLevel(serverConfig.leakDetectionLevel.jResourceLeakDetectionLevel))
      port <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield port

}

object NettyDriver {

  val make =
    for {
      cf    <- ZIO.service[ChannelFactory[ServerChannel]]
      cInit <- ZIO.service[ChannelInitializer[Channel]]
      elg   <- ZIO.service[EventLoopGroup]
      sc    <- ZIO.service[ServerConfig]
    } yield {
      val d: Driver = new NettyDriver(
        channelFactory = cf,
        channelInitializer = cInit,
        eventLoopGroup = elg,
        serverConfig = sc,
      )
      d
    }

  val default = ZLayer.scopedEnvironment {
    val context = Driver.Context.empty
    val time    = ZLayer.succeed(ServerTime.make(1000 millis))
    make
      .provideSome[ServerConfig & Scope](
        ZLayer.succeed(context),
        time,
        ServerChannelInitializer.layer,
        ServerInboundHandler.layer,
        ChannelFactories.Server.fromConfig,
        EventLoopGroups.fromConfig,
        NettyRuntime.usingSharedThreadPool,
      )
      .map(d => ZEnvironment(d, context))

  }

}
