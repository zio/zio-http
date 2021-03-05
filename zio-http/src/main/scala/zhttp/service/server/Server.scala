package zhttp.service.server

import zhttp.core._
import zhttp.http._
import zhttp.service.{ChannelFuture, EventLoopGroup, UnsafeChannelExecutor}
import zio._

final class Server[R](serverBootstrap: JServerBootstrap, init: JChannelInitializer[JChannel]) { self =>

  /**
   * Starts the server on the provided port
   */
  def start(port: Int): Task[Unit] =
    ChannelFuture.unit(serverBootstrap.childHandler(init).bind(port)).unit
}

object Server {
  type SilentResponse[E] = CanBeSilenced[E, Response]

  def make[R, E: SilentResponse](http: HttpApp[R, E]): ZIO[R with EventLoopGroup, Throwable, Server[R]] =
    for {
      zExec          <- UnsafeChannelExecutor.make[R]
      channelFactory <- ServerChannelFactory.Live.auto
      eventLoopGroup <- ZIO.access[EventLoopGroup](_.get)
    } yield {
      val httpH           = ServerRequestHandler(zExec, http.silent)
      val init            = ServerChannelInitializer(httpH)
      val serverBootstrap = new JServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      new Server(serverBootstrap, init)
    }

  /**
   * Makes a new Server and starts it immediately using the default EventLoopGroup.
   */
  def start[R <: Has[_], E: SilentResponse](port: Int, http: HttpApp[R, E]): ZIO[R, Throwable, Nothing] =
    make(http).flatMap(_.start(port) *> ZIO.never).provideSomeLayer[R](EventLoopGroup.auto())
}
