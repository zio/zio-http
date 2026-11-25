package zio.http

import scala.annotation.nowarn

import zio.ZLayer

/** The platform part of the `Driver` companion: the default driver layer. */
@nowarn("msg=dead code")
trait DriverPlatformSpecific {

  /**
   * A layer that builds the Netty server `Driver` from a `Server.Config`, with
   * `NettyConfig.default`. On Scala.js it is not implemented and throws an
   * `UnsupportedOperationException`.
   */
  val default: ZLayer[Server.Config, Throwable, Driver] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")
}
