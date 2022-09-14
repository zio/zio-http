package zio.http
package netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.util.ResourceLeakDetector
import zio._
import zio.http.service.Log

import java.net.InetSocketAddress
// import io.netty.handler.codec.http.HttpObject

private[zio] final case class NettyDriver(
  channelFactory: ChannelFactory[ServerChannel],
  channelInitializer: ChannelInitializer[Channel],
  eventLoopGroup: EventLoopGroup,
  serverConfig: ServerConfig,
) extends Driver { self =>

  private[zio] val log = NettyDriver.log

  def start(): RIO[Scope, Int] =
    for {
      // rtm <- HttpRuntime.sticky[Any](eventLoopGroup)
      // time <- ZIO.succeed(service.ServerTime.make(1000 millis))
      // appRef          = new AtomicReference[HttpApp[Any, Throwable]](Http.empty)
      // reqHandler <- ZIO.succeed(ServerInboundHandler(appRef, rtm, serverConfig, time))
      // init            = ServerChannelInitializer(rtm, serverConfig, reqHandler)
      serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
      chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
      _               <- NettyFutureExecutor.scoped(chf)
      _    <- ZIO.succeed(ResourceLeakDetector.setLevel(serverConfig.leakDetectionLevel.jResourceLeakDetectionLevel))
      port <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield port

  // for {
  //   //   channelFactory <- ZIO.service[ServerChannelFactory]
  //   //   eventLoopGroup <- ZIO.service[EventLoopGroup]
  //   // rtm <- NettyDriver.sticky[R](eventLoopGroup)
  //   // time            = service.ServerTime.make(1000 millis)
  //   //   reqHandler      = ServerInboundHandler(settings.app, rtm, settings, time)
  //   // init            = ServerChannelInitializer(rtm, serverConfig, reqHandler)
  //   serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
  //   chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
  //   _               <- NettyFutureExecutor.scoped(chf)
  //   port            <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
  // } yield {
  //   ResourceLeakDetector.setLevel(serverConfig.leakDetectionLevel.jResourceLeakDetectionLevel)
  //   log.info(s"Server Started on Port: [${port}]")
  //   log.debug(s"Keep Alive: [${serverConfig.keepAlive}]")
  //   log.debug(s"Leak Detection: [${serverConfig.leakDetectionLevel}]")
  //   log.debug(s"Transport: [${eventLoopGroup.getClass.getName}]")
  //   port
  // }

}

object NettyDriver {

  private[zio] val log = Log.withTags("NettyDriver")

  // type Env = ChannelFactory[ServerChannel]
  //   with ChannelInitializer[Channel]
  //   with EventLoopGroup
  //   with ServerConfig

  val layer =
    ZLayer.fromZIO {
      for {
        cf    <- ZIO.service[ChannelFactory[ServerChannel]]
        cInit <- ZIO.service[ChannelInitializer[Channel]]
        elg   <- ZIO.service[EventLoopGroup]
        sc    <- ZIO.service[ServerConfig]
      } yield new NettyDriver(
        channelFactory = cf,
        channelInitializer = cInit,
        eventLoopGroup = elg,
        serverConfig = sc,
      )
    }

}
