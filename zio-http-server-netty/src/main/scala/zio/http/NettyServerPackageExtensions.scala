package zio.http

import zio._
import zio.http.internal._
import zio.http.netty._
import zio.http.netty.server.NettyDriver

trait NettyServerPackageExtensions {

  implicit val queryParamEncoding: QueryParamEncoding = NettyQueryParamEncoding

  implicit val cookieEncoding: CookieEncoding = NettyCookieEncoding

  val customized: ZLayer[Server.Config & NettyConfig, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    NettyDriver.customized >>> Server.base
  }

  val live: ZLayer[Server.Config, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    NettyDriver.live >+> Server.base
  }

}
