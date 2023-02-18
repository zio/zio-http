package zio.http

import zio.{Promise, Scope, Trace, ZIO, ZLayer}

import zio.http.ClientDriver.ChannelInterface
import zio.http.netty.client.ChannelState
import zio.http.socket.SocketApp

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
  )(implicit trace: Trace): ZIO[Scope, Throwable, ChannelInterface]

  def createConnectionPool(config: ConnectionPoolConfig)(implicit
    trace: Trace,
  ): ZIO[Scope, Nothing, ConnectionPool[Connection]]
}

object ClientDriver {
  trait ChannelInterface {
    def resetChannel(): ZIO[Any, Throwable, ChannelState]
    def interrupt(): ZIO[Any, Throwable, Unit]
  }

  val shared: ZLayer[ClientConfig with Driver, Throwable, ClientDriver] =
    ZLayer.scoped {
      for {
        config       <- ZIO.service[ClientConfig]
        driver       <- ZIO.service[Driver]
        clientDriver <- driver.createClientDriver(config)
      } yield clientDriver
    }
}
