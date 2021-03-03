package zhttp.service.netty.server

import zhttp.core.netty._
import zhttp.domain.http._
import zhttp.domain.http.model.{HttpError, Response}
import zhttp.service.netty.{ChannelFuture, EventLoopGroup, ServerChannelFactory, UnsafeChannelExecutor}
import zio._

final class Server[R](serverBootstrap: JServerBootstrap, init: JChannelInitializer[JChannel]) { self =>

  /**
   * Starts the server on the provided port
   */
  def start(port: Int): Task[Unit] =
    ChannelFuture.unit(serverBootstrap.childHandler(init).bind(port)).unit
}

object Server {

  def make[R, E >: HttpError](
    http: HttpApp[R, E],
  )(implicit
    ev: CanBeSilenced[E, Response],
  ): ZIO[R with ServerChannelFactory with EventLoopGroup, Throwable, Server[R]] =
    for {
      zExec          <- UnsafeChannelExecutor.make[R]
      channelFactory <- ZIO.access[ServerChannelFactory](_.get)
      eventLoopGroup <- ZIO.access[EventLoopGroup](_.get)
      httpH           = ServerRequestHandler(zExec, http.silent)
      init            = ServerChannelInitializer(httpH)
      serverBootstrap = new JServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
    } yield new Server(serverBootstrap, init)

  /**
   * Makes a new Http Server and Starts it immediately
   */
  def start[R](port: Int)(
    http: HttpApp[R, Nothing],
  ): ZIO[R with ServerChannelFactory with EventLoopGroup, Throwable, Nothing] =
    make(http).flatMap(_.start(port) *> ZIO.never)
}
