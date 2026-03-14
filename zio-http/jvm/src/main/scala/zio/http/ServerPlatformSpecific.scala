package zio.http

import scala.annotation.nowarn

import zio._

import zio.http.Server.Config

@nowarn("msg=dead code")
trait ServerPlatformSpecific {

  private[http] def base: ZLayer[Driver & Config, Throwable, Server]

  def customized: ZLayer[Config, Throwable, Driver with Server] =
    throw new UnsupportedOperationException(
      "No Server implementation available. Add zio-http-netty to your dependencies.",
    )

  def live: ZLayer[Config, Throwable, Server with Driver] =
    throw new UnsupportedOperationException(
      "No Server implementation available. Add zio-http-netty to your dependencies.",
    )

}
