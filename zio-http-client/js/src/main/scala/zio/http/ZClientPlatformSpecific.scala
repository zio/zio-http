package zio.http

import zio._

import zio.http.ZClient.Config

trait ZClientPlatformSpecific {

  type Client = ZClient[Any, Scope, Body, Throwable, Response]

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

}
