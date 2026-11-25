package zio.http

import zio._

import zio.http.Server.Config
import zio.http.netty.NettyConfig
import zio.http.netty.server._

/**
 * The platform part of the `Server` companion: the layers that build a
 * [[Server]] together with its `Driver`.
 */
trait ServerPlatformSpecific {

  private[http] def base: ZLayer[Driver & Config, Throwable, Server]

  /**
   * A layer that builds a [[Server]] and its Netty `Driver` from a
   * `Server.Config` and a `NettyConfig`, which sets the Netty channel type,
   * thread counts and shutdown timing. On Scala.js it needs only a
   * `Server.Config`, is not implemented, and throws an
   * `UnsupportedOperationException`.
   */
  val customized: ZLayer[Config & NettyConfig, Throwable, Driver with Server] = {
    // tmp val Needed for Scala2
    val tmp: ZLayer[Driver & Config, Throwable, Server] = ZLayer.suspend(base)

    ZLayer.makeSome[Config & NettyConfig, Driver with Server](
      NettyDriver.customized,
      tmp,
    )
  }

  /**
   * A layer that builds a [[Server]] and its Netty `Driver` from a
   * `Server.Config`, with `NettyConfig.default`. On Scala.js it is not
   * implemented and throws an `UnsupportedOperationException`.
   */
  val live: ZLayer[Config, Throwable, Server with Driver] = {
    // tmp val Needed for Scala2
    val tmp: ZLayer[Driver & Config, Throwable, Server] = ZLayer.suspend(base)

    ZLayer.makeSome[Config, Server with Driver](
      NettyDriver.live,
      tmp,
    )
  }

}
