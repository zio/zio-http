package zio.http

import zio._

import zio.http.Server.Config

trait ServerPlatformSpecific {

  private[http] def base: ZLayer[Driver & Config, Throwable, Server]

  def customized: ZLayer[Config, Throwable, Driver with Server] =
    ZLayer.fail(
      new UnsupportedOperationException(
        "No Server implementation available. Add zio-http-netty to your dependencies.",
      ),
    )

  def live: ZLayer[Config, Throwable, Server with Driver] =
    ZLayer.fail(
      new UnsupportedOperationException(
        "No Server implementation available. Add zio-http-netty to your dependencies.",
      ),
    )

}
