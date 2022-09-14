package zio.http
package netty

import zio._
import io.netty.channel._
import java.net.InetSocketAddress
import io.netty.bootstrap.ServerBootstrap
import service.Log
import io.netty.util.ResourceLeakDetector
import io.netty.handler.codec.http.HttpObject

private[zio] final case class NettyDriver[R](
  channelFactory: ChannelFactory[ServerChannel],
  channelInitializer: ChannelInitializer[Channel],
  eventLoopGroup: EventLoopGroup,
  requestHandler: SimpleChannelInboundHandler[HttpObject],
  serverConfig: Server.Config[R, Throwable],
) extends Driver[R] { self =>

  private[zio] val log = NettyDriver.log

  def start(httpApp: HttpApp[R, Throwable]): RIO[R with Scope, Server.Start] =
    for {
      //   channelFactory <- ZIO.service[ServerChannelFactory]
      //   eventLoopGroup <- ZIO.service[EventLoopGroup]
      // rtm <- NettyDriver.sticky[R](eventLoopGroup)
      // time            = service.ServerTime.make(1000 millis)
      //   reqHandler      = ServerInboundHandler(settings.app, rtm, settings, time)
      // init            = ServerChannelInitializer(rtm, serverConfig, reqHandler)
      serverBootstrap <- ZIO.attempt(new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup))
      chf             <- ZIO.attempt(serverBootstrap.childHandler(channelInitializer).bind(serverConfig.address))
      _               <- NettyFutureExecutor.scoped(chf)
      port            <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield {
      ResourceLeakDetector.setLevel(serverConfig.leakDetectionLevel.jResourceLeakDetectionLevel)
      log.info(s"Server Started on Port: [${port}]")
      log.debug(s"Keep Alive: [${serverConfig.keepAlive}]")
      log.debug(s"Leak Detection: [${serverConfig.leakDetectionLevel}]")
      log.debug(s"Transport: [${eventLoopGroup.getClass.getName}]")
      Server.Start(port)
    }

}

object NettyDriver {

  private[zio] val log = Log.withTags("NettyDriver")

  type Env[R] = R
    with ChannelFactory[ServerChannel]
    with ChannelInitializer[Channel]
    with EventLoopGroup
    with Server.Config[R, Throwable]
    with SimpleChannelInboundHandler[HttpObject]

  def layer[R: Tag]: URIO[Env[R], Driver[R]] =
    for {
      cf         <- ZIO.service[ChannelFactory[ServerChannel]]
      cInit      <- ZIO.service[ChannelInitializer[Channel]]
      elg        <- ZIO.service[EventLoopGroup]
      reqHandler <- ZIO.service[SimpleChannelInboundHandler[HttpObject]]
      sc         <- ZIO.service[Server.Config[R, Throwable]]
    } yield new NettyDriver(
      channelFactory = cf,
      channelInitializer = cInit,
      eventLoopGroup = elg,
      requestHandler = reqHandler,
      serverConfig = sc,
    )

}
