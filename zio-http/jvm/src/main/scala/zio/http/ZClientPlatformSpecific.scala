package zio.http

import scala.annotation.nowarn

import zio._

import zio.http.ZClient.Config

@nowarn("msg=dead code")
trait ZClientPlatformSpecific {

  def customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client] =
    throw new UnsupportedOperationException(
      "No Client implementation available. Add zio-http-netty to your dependencies.",
    )

  def live: ZLayer[Config with DnsResolver, Throwable, Client] =
    throw new UnsupportedOperationException(
      "No Client implementation available. Add zio-http-netty to your dependencies.",
    )

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
  )(implicit trace: Trace): ZLayer[DnsResolver, Throwable, Client] =
    throw new UnsupportedOperationException(
      "No Client implementation available. Add zio-http-netty to your dependencies.",
    )

  def default: ZLayer[Any, Throwable, Client] =
    throw new UnsupportedOperationException(
      "No Client implementation available. Add zio-http-netty to your dependencies.",
    )

}
