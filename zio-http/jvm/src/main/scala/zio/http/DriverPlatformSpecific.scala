package zio.http

import zio.ZLayer

import zio.http.netty.server.NettyDriver

/** The platform part of the `Driver` companion: the default driver layer. */
trait DriverPlatformSpecific {

  /**
   * A layer that builds the Netty server `Driver` from a `Server.Config`, with
   * `NettyConfig.default`. On Scala.js it is not implemented: its value is defined as a `throw` of an
   * `UnsupportedOperationException`.
   */
  val default: ZLayer[Server.Config, Throwable, Driver] =
    NettyDriver.live
}
