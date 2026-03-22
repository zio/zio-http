package zio.http.netty.server

import zio._

import zio.http._
import zio.http.netty.NettyConfig

object NettyServer {

  val customized: ZLayer[Server.Config & NettyConfig, Throwable, Driver with Server] = {
    implicit val trace: Trace                                  = Trace.empty
    val tmp: ZLayer[Driver & Server.Config, Throwable, Server] = ZLayer.suspend(Server.base)

    ZLayer.makeSome[Server.Config & NettyConfig, Driver with Server](
      NettyDriver.customized,
      tmp,
    )
  }

  val live: ZLayer[Server.Config, Throwable, Server with Driver] = {
    implicit val trace: Trace                                  = Trace.empty
    val tmp: ZLayer[Driver & Server.Config, Throwable, Server] = ZLayer.suspend(Server.base)

    ZLayer.makeSome[Server.Config, Server with Driver](
      NettyDriver.live,
      tmp,
    )
  }

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "server"),
  )(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    ZLayer(ZIO.config(Server.Config.config.nested(path.head, path.tail: _*))).mapError(error =>
      new RuntimeException(s"Configuration error: $error"),
    ) >>> live

  def defaultWithPort(port: Int)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    defaultWith(_.port(port))

  def defaultWith(f: Server.Config => Server.Config)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    ZLayer.succeed(f(Server.Config.default)) >>> live

  val default: ZLayer[Any, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(Server.Config.default) >>> live
  }

}
