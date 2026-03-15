package zio.http

import zio.ZLayer

trait DriverPlatformSpecific {
  def default: ZLayer[Server.Config, Throwable, Driver] =
    ZLayer.fail(
      new UnsupportedOperationException(
        "No Driver implementation available. Add zio-http-netty to your dependencies.",
      ),
    )
}
