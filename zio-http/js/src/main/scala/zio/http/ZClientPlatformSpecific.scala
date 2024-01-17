package zio.http

import zio._

import zio.http.internal.FetchDriver

trait ZClientPlatformSpecific {

//  def customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client]

  lazy val live: ZLayer[ZClient.Config, Throwable, Client] =
    default

  // TODO should probably exist in js too
//  def configured(
//    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
//  )(implicit trace: Trace): ZLayer[DnsResolver, Throwable, Client] =
//    (
//      ZLayer.service[DnsResolver] ++
//        ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*))) ++
//        ZLayer(ZIO.config(NettyConfig.config.nested(path.head, path.tail: _*)))
//      ).mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  lazy val default: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    FetchDriver.live >>> ZLayer(ZIO.serviceWith[FetchDriver](driver => ZClient.fromDriver(driver)))
  }

}
