package zio.http

import zio._

import zio.http.Server.Config

trait ServerPlatformSpecific {
  val customized: ZLayer[Config, Throwable, Server] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")

  val live: ZLayer[Config, Throwable, Server] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")
}
