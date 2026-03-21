package zio.http

import zio.{Scope, Trace, ZIO, ZLayer}

import zio.http.ZClient.Config

trait ZClientPlatformSpecific {

  type Client = ZClient[Any, Scope, Body, Throwable, Response]

  def customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        config         <- ZIO.service[Config]
        driver         <- ZIO.service[ClientDriver]
        dnsResolver    <- ZIO.service[DnsResolver]
        connectionPool <- driver.createConnectionPool(dnsResolver, config.connectionPool)
        baseClient = ZClient.fromDriver(new ZClient.DriverLive(driver)(connectionPool)(config))
      } yield
        if (config.addUserAgentHeader)
          baseClient.addHeader(ZClient.defaultUAHeader)
        else
          baseClient
    }
  }

}
