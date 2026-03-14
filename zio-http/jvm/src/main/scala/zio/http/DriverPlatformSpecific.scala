package zio.http

import scala.annotation.nowarn

import zio.ZLayer

@nowarn("msg=dead code")
trait DriverPlatformSpecific {
  def default: ZLayer[Server.Config, Throwable, Driver] =
    throw new UnsupportedOperationException(
      "No Driver implementation available. Add zio-http-netty to your dependencies.",
    )
}
