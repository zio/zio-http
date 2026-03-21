package zio.http.netty.client

import zio._

import zio.http._
import zio.http.netty.NettyConfig

object NettyClient {

  val live: ZLayer[ZClient.Config with NettyConfig with DnsResolver, Throwable, ZClient.Client] = {
    implicit val trace: Trace = Trace.empty
    (NettyClientDriver.live ++ ZLayer.service[DnsResolver]) >>> ZClient.customized
  }.fresh

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
  )(implicit trace: Trace): ZLayer[DnsResolver, Throwable, ZClient.Client] =
    (
      ZLayer.service[DnsResolver] ++
        ZLayer(ZIO.config(ZClient.Config.config.nested(path.head, path.tail: _*))) ++
        ZLayer(ZIO.config(NettyConfig.config.nested(path.head, path.tail: _*)))
    ).mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  val default: ZLayer[Any, Throwable, ZClient.Client] = {
    implicit val trace: Trace = Trace.empty
    (ZLayer.succeed(ZClient.Config.default) ++ ZLayer.succeed(NettyConfig.defaultWithFastShutdown) ++
      DnsResolver.default) >>> live
  }

}
