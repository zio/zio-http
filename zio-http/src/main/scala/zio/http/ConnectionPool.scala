package zio.http

import zio.{Scope, Trace, ZIO}

import java.net.InetSocketAddress

trait ConnectionPool[Connection] {

  def get(
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxHeaderSize: Int,
    decompression: Decompression,
    localAddress: Option[InetSocketAddress] = None,
  ): ZIO[Scope, Throwable, Connection]

  def invalidate(connection: Connection): ZIO[Any, Nothing, Unit]

  def enableKeepAlive: Boolean
}
