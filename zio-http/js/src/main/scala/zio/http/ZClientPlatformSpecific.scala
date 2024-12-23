package zio.http

import zio._

import zio.http.ZClient.Config
import zio.http.internal.FetchDriver

trait ZClientPlatformSpecific {

  def customized: ZLayer[Config with ZClient.Driver[Any, Scope, Throwable], Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        config <- ZIO.service[Config]
        driver <- ZIO.service[ZClient.Driver[Any, Scope, Throwable]]
        baseClient = ZClient.fromDriver(driver)
      } yield
        if (config.addUserAgentHeader)
          baseClient.addHeader(ZClient.defaultUAHeader)
        else
          baseClient
    }
  }

  def live: ZLayer[ZClient.Config, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    FetchDriver.live >>> customized
  }.fresh

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
  )(implicit trace: Trace): ZLayer[Any, Throwable, Client] =
    ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*)))
      .mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  def default: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(Config.default) >>> live
  }

}
