package zio.http

import zio.{NonEmptyChunk, Trace, ZIO, ZLayer}

import zio.http.ZClient.Config
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

trait ZClientPlatformSpecific {

  def customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client]

  lazy val live: ZLayer[ZClient.Config with NettyConfig with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (NettyClientDriver.live ++ ZLayer.service[DnsResolver]) >>> customized
  }.fresh

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
  )(implicit trace: Trace): ZLayer[DnsResolver, Throwable, Client] =
    (
      ZLayer.service[DnsResolver] ++
        ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*))) ++
        ZLayer(ZIO.config(NettyConfig.config.nested(path.head, path.tail: _*)))
    ).mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  lazy val default: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (ZLayer.succeed(Config.default) ++ ZLayer.succeed(NettyConfig.defaultWithFastShutdown) ++
      DnsResolver.default) >>> live
  }

}
