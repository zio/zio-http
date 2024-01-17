package zio.http

import zio._

import zio.http.Server.Config
import zio.http.netty.NettyConfig
import zio.http.netty.server._

trait ServerPlatformSpecific {

  private[http] val base: ZLayer[Driver & Config, Throwable, Server]

  val customized: ZLayer[Config & NettyConfig, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    NettyDriver.customized >>> base
  }

  val live: ZLayer[Config, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    NettyDriver.live >+> base
  }

}
