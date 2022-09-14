package zio.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.util.ResourceLeakDetector
import zio.http.service._
import zio.{UIO, URIO, ZIO, ZLayer, durationInt}

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

trait Server {
  def serve[R](httpApp: HttpApp[R, Throwable]): URIO[R, Unit]

  def port: UIO[Int]
}

object Server {
  def serve[R](httpApp: HttpApp[R, Throwable]): URIO[R with Server, Unit] =
    ZIO.serviceWithZIO[Server](_.serve(httpApp)) *> ZIO.never

  val default = ServerConfigLayer.default >>> live

  val live: ZLayer[ServerConfig with EventLoopGroup with ServerChannelFactory, Throwable, Server] = ZLayer.scoped {
    for {
      channelFactory <- ZIO.service[ServerChannelFactory]
      eventLoopGroup <- ZIO.service[EventLoopGroup]
      settings <- ZIO.service[ServerConfig]
      rtm <- HttpRuntime.sticky[Any](eventLoopGroup)
      time = ServerTime.make(1000 millis)
      appRef = new AtomicReference[HttpApp[Any, Throwable]](Http.empty)
      reqHandler <- ZIO.succeed(ServerInboundHandler(appRef, rtm, settings, time))
      init = ServerChannelInitializer(rtm, settings, reqHandler)
      serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      chf <- ZIO.attempt(serverBootstrap.childHandler(init).bind(settings.address))
      _ <- ChannelFuture.scoped(chf)
      _ <- ZIO.succeed(ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel))
      port <- ZIO.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield ServerLive(appRef, port)
  }

  val test = ServerConfigLayer.testServerConfig >>> live

  private final case class ServerLive(
                                       appRef: java.util.concurrent.atomic.AtomicReference[HttpApp[Any, Throwable]],
                                       bindPort: Int
                                     ) extends Server {
    override def serve[R](httpApp: HttpApp[R, Throwable]): URIO[R, Unit] =
      ZIO.environment[R].map { env =>
        val newApp = httpApp.provideEnvironment(env)
        var loop = true
        while (loop) {
          val oldApp = appRef.get()
          if (appRef.compareAndSet(oldApp, newApp ++ oldApp)) loop = false
        }
        ()
      }

    override def port: UIO[Int] = ZIO.succeed(bindPort)
  }

}
