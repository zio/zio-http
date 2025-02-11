package zio.http

import scala.annotation.nowarn

import zio.ZLayer

@nowarn("msg=dead code")
trait DriverPlatformSpecific {
  val default: ZLayer[Server.Config, Throwable, Driver] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")
}
