package zhttp.service

import io.netty.util.{ResourceLeakDetector => JResourceLeakDetector}
import zhttp.core._
import zhttp.http._
import zhttp.service.server.{ServerChannelFactory, ServerChannelInitializer, ServerRequestHandler}
import zio._

final class Server[R](serverBootstrap: JServerBootstrap, init: JChannelInitializer[JChannel]) { self =>

  /**
   * Starts the server on the provided port
   */
  def start(port: Int): Task[Unit] =
    ChannelFuture.unit(serverBootstrap.childHandler(init).bind(port)).unit
}

object Server {
  def make[R](http: HttpApp[R, Nothing]): ZIO[R with EventLoopGroup, Throwable, Server[R]] =
    for {
      zExec          <- UnsafeChannelExecutor.make[R]
      channelFactory <- ServerChannelFactory.Live.auto
      eventLoopGroup <- ZIO.access[EventLoopGroup](_.get)
    } yield {
      val init            = ServerChannelInitializer(() => ServerRequestHandler(zExec, http))
      val serverBootstrap = new JServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)

      // Disabling default leak detection
      resetLeakDetection()
      new Server(serverBootstrap, init)
    }

  private def resetLeakDetection(): Unit = {
    if (
      System.getProperty("io.netty.leakDetection.level") == null &&
      System.getProperty("io.netty.leakDetectionLevel") == null
    ) JResourceLeakDetector.setLevel(JResourceLeakDetector.Level.DISABLED)
  }

  /**
   * Launches the app on the provided port.
   */
  def start[R <: Has[_]](port: Int, http: HttpApp[R, Nothing]): ZIO[R, Throwable, Nothing] =
    make(http).flatMap(_.start(port) *> ZIO.never).provideSomeLayer[R](EventLoopGroup.auto())
}
