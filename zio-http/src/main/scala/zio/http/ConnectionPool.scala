package zio.http

import java.net.InetSocketAddress

import zio.{Scope, Trace, ZIO}

trait ConnectionPool[Connection] {

  def get(
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
    localAddress: Option[InetSocketAddress] = None,
  )(implicit trace: Trace): ZIO[Scope, Throwable, Connection]

  def invalidate(connection: Connection)(implicit trace: Trace): ZIO[Any, Nothing, Unit]

  def enableKeepAlive: Boolean
}
