package zio.http

import zio._

import zio.http.Server.Config
import zio.http.netty.NettyConfig
import zio.http.netty.server._

trait ServerPlatformSpecific {

  private[http] def base: ZLayer[Driver & Config, Throwable, Server]

  val customized: ZLayer[Config & NettyConfig, Throwable, Driver with Server] = {
    // tmp val Needed for Scala2
    val tmp: ZLayer[Driver & Config, Throwable, Server] = ZLayer.suspend(base)

    ZLayer.makeSome[Config & NettyConfig, Driver with Server](
      NettyDriver.customized,
      tmp,
    )
  }

  val live: ZLayer[Config, Throwable, Server with Driver] = {
    // tmp val Needed for Scala2
    val tmp: ZLayer[Driver & Config, Throwable, Server] = ZLayer.suspend(base)

    ZLayer.makeSome[Config, Server with Driver](
      NettyDriver.live,
      tmp,
    )
  }

}
