package zio.http

import zio.{Scope, Task, ULayer, ZIO, ZLayer}

private[http] trait Network {
  def findOpenPort: Task[Int]
}

private[http] object Network {
  def findOpenPort: ZIO[Network, Throwable, Int] =
    ZIO.serviceWithZIO[Network](_.findOpenPort)

  val live: ULayer[Network] = ZLayer.succeed(NetworkLive)

  case object NetworkLive extends Network {

    def findOpenPort: Task[Int] =
      ZIO
        .acquireRelease(
          ZIO.logDebug(s"Attempting to find an open network port...") *>
            ZIO.attemptBlocking(new java.net.ServerSocket(0)),
        )(socket =>
          ZIO.attemptBlocking(socket.close()).ignoreLogged <* ZIO.logDebug(
            s"Successfully closed socket bound on ${socket.getLocalPort}.",
          ),
        )
        .map(_.getLocalPort)
        .tap(p => ZIO.logDebug(s"An open port was found on $p."))
        // Providing the default scope here guarantees that the 'release' function is invoked
        // and the socket is closed immediately after the call to `map` .
        .provide(Scope.default)
  }
}
