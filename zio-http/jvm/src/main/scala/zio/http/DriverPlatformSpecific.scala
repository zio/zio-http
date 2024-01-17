package zio.http

import zio.ZLayer
import zio.http.netty.server.NettyDriver

trait DriverPlatformSpecific {
  val default: ZLayer[Server.Config, Throwable, Driver] =
    NettyDriver.live
}
