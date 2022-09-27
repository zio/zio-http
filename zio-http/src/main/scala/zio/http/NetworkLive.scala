package zio.http

import zio.{IO, Scope, ZIO}

case object NetworkLive {

  def findOpenPort: IO[Any, Int] =
    ZIO
      .acquireRelease(
        ZIO.logDebug(s"Attempting to find an open network port...") *> ZIO.attemptBlocking(new java.net.ServerSocket(0))
      )(socket =>
        ZIO.attemptBlocking(socket.close()).ignoreLogged <* ZIO.logDebug(
          s"Successfully closed socket bound on ${socket.getLocalPort}."
        )
      )
      .map(_.getLocalPort)
      //      .mapError(Docker.invalidRuntimeState(s"Unable to find an open network port"))
      .tap(p => ZIO.logDebug(s"An open port was found on $p."))
      // Providing the default scope here guarantees that the 'release' function is invoked
      // and the socket is closed immediately after the call to `map` .
      .provide(Scope.default)
}
