package zio.http

import scala.annotation.nowarn

import zio._

import zio.http.Server.Config

/**
 * The platform part of the `Server` companion: the layers that build a
 * [[Server]] together with its `Driver`.
 */
@nowarn("msg=dead code")
trait ServerPlatformSpecific {

  /**
   * A layer that builds a [[Server]] and its Netty `Driver` from a
   * `Server.Config` and a `NettyConfig`, which sets the Netty channel type,
   * thread counts and shutdown timing. On Scala.js its type needs only a
   * `Server.Config`, and it is not implemented: its value is defined as a
   * `throw` of an `UnsupportedOperationException`.
   */
  val customized: ZLayer[Config, Throwable, Server] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")

  /**
   * A layer that builds a [[Server]] and its Netty `Driver` from a
   * `Server.Config`, with `NettyConfig.default`. On Scala.js it is not
   * implemented: its value is defined as a `throw` of an
   * `UnsupportedOperationException`.
   */
  val live: ZLayer[Config, Throwable, Server] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")
}
