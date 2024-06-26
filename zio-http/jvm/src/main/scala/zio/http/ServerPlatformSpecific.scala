package zio.http

import zio._

import zio.http.Server.Config
import zio.http.netty.NettyConfig
import zio.http.netty.server._

trait ServerPlatformSpecific {

  private[http] val base: ZLayer[Driver & Config, Throwable, Server]

  val customized: ZLayer[Config & NettyConfig, Throwable, Driver with Server] = {
    implicit val trace: Trace = Trace.empty

    val baseExtra: ZLayer[Driver & Config, Throwable, Server with Driver] = ZLayer.suspend(base) >+> ZLayer.environment[Driver]
    val nettyExtra: ZLayer[Config & NettyConfig, Throwable, Driver with Config] = NettyDriver.customized ++ ZLayer.environment[Config]
    val composed: ZLayer[Config & NettyConfig, Throwable, Server with Driver] = nettyExtra >>> baseExtra
    //NettyDriver.customized >>> base
    composed
  }

  val live: ZLayer[Config, Throwable, Server with Driver] = {
    implicit val trace: Trace = Trace.empty
    val baseExtra: ZLayer[Driver & Config, Throwable, Server with Driver] = ZLayer.suspend(base) >+> ZLayer.environment[Driver]
    val nettyExtra: ZLayer[Config, Throwable, Driver with Config] = NettyDriver.live ++ ZLayer.environment[Config]
    val res: ZLayer[Config, Throwable, Server with Driver] = nettyExtra >>> baseExtra
    res
  }

}
