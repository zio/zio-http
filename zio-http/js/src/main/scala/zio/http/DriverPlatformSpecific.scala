package zio.http

import zio.ZLayer

trait DriverPlatformSpecific {
  val default: ZLayer[Server.Config, Throwable, Driver] =
    throw new UnsupportedOperationException("Not implemented for Scala.js")
}
