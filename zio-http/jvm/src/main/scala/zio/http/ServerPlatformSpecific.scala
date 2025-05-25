package zio.http

import zio._

import zio.http.Server.Config
import zio.http.netty.NettyConfig
import zio.http.netty.server._

trait ServerPlatformSpecific {

  private[http] def base: ZLayer[Driver & Config, Throwable, Server]

  val customized: ZLayer[ServerRuntimeConfig & NettyConfig, Throwable, Driver with Server] = {
    // tmp val Needed for Scala2
    val tmp: ZLayer[Driver & Config, Throwable, Server] = ZLayer.suspend(base)

    ZLayer.makeSome[ServerRuntimeConfig & NettyConfig, Driver with Server](
      ZLayer.fromFunction((runtime: ServerRuntimeConfig) => runtime.config),
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
