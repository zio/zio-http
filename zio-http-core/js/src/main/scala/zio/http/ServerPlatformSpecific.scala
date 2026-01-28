package zio.http

import scala.annotation.nowarn

import zio._

import zio.http.Server.Config

@nowarn("msg=dead code")
trait ServerPlatformSpecific {
  val customized: ZLayer[Config, Throwable, Server] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")

  val live: ZLayer[Config, Throwable, Server] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")
}
