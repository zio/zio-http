package zio.http

import zio.{Task, ULayer, ZIO, ZLayer, Scope}

import java.net.ServerSocket

private[http] trait Network {
  def findOpenPort: Task[Int]

  val socketOnOpenPort: ZIO[Scope, Throwable, ServerSocket]
}

private[http] object Network {
  def findOpenPort: ZIO[Network, Throwable, Int] =
    ZIO.serviceWithZIO[Network](_.findOpenPort)

  val socketOnOpenPort: ZIO[Scope with Network, Throwable, ServerSocket] =
    ZIO.serviceWithZIO[Network](_.socketOnOpenPort)


  val live: ULayer[Network] = ZLayer.succeed(NetworkLive)

  case object NetworkLive extends Network {

    val socketOnOpenPort: ZIO[Scope, Throwable, ServerSocket] =
      ZIO
        .acquireRelease(
          ZIO.logDebug(s"Attempting to find an open network port...") *>
            ZIO.attemptBlocking(new java.net.ServerSocket(0)),
        )(socket =>
          ZIO.attemptBlocking(socket.close()).ignoreLogged <* ZIO.logDebug(
            s"Successfully closed socket bound on ${socket.getLocalPort}.",
          ),
        )

    def findOpenPort: Task[Int] =
      ZIO
        .acquireReleaseWith(
          ZIO.logDebug(s"Attempting to find an open network port...") *>
            ZIO.attemptBlocking(new java.net.ServerSocket(0)),
        )(socket =>
          ZIO.attemptBlocking(socket.close()).ignoreLogged <* ZIO.logDebug(
            s"Successfully closed socket bound on ${socket.getLocalPort}.",
          ),
        )
        .apply(x => ZIO.succeed(x.getLocalPort))
        .tap(p => ZIO.logDebug(s"An open port was found on $p."))
  }
}
