package zio.http

import zio.http.netty.client.ChannelState
import zio.http.socket.SocketApp
import zio.{Promise, Scope, Trace, ZIO}

trait ClientDriver {
  type Connection

  def requestOnChannel(
    connection: Connection,
    location: URL.Location.Absolute,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    useAggregator: Boolean,
    enableKeepAlive: Boolean,
    createSocketApp: () => SocketApp[Any],
  )(implicit trace: Trace): ZIO[Scope, Throwable, ChannelState]

  def createConnectionPool(config: ConnectionPoolConfig)(implicit
    trace: Trace,
  ): ZIO[Scope, Nothing, ConnectionPool[Connection]]
}
