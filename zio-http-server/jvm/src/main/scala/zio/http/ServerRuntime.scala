package zio.http

import java.io.IOException
import java.net.{InetSocketAddress, ServerSocket}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

import scala.jdk.CollectionConverters._

import zio.blocks.context.Context

private[http] object ServerRuntime extends ServerRuntimePlatform {
  override def serve[Ctx](server: Server[Ctx], context: Context[Ctx]): ServerHandle = {
    val bound = server.connectorsList.map(bindConnector)
    ServerHandle.live(bound)
  }

  private def bindConnector(connector: Connector): BoundConnectorHandle =
    connector.bind match {
      case BindAddress.Tcp(host, port) =>
        connector.protocol match {
          case _: Protocol.H2C => bindH2c(host, port, connector.protocol)
          case _: Protocol.H2  => throw new UnsupportedOperationException("TLS-backed H2 is not implemented yet")
          case _: Protocol.H3  => throw new UnsupportedOperationException("H3/QUIC is not implemented yet")
        }
      case BindAddress.Unix(path)      =>
        throw new UnsupportedOperationException(s"Unix domain sockets are not implemented yet: $path")
    }

  private def bindH2c(host: String, port: Int, protocol: Protocol): BoundConnectorHandle = {
    val socket   = new ServerSocket()
    val address  = new InetSocketAddress(host, port)
    socket.bind(address)
    val running  = new AtomicBoolean(true)
    val acceptor = Thread.ofVirtual().name(s"zio-http-h2c-$host:$port").start(() => acceptLoop(socket, running))
    val local    = socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress]

    BoundConnectorHandle(
      BoundConnector(BoundAddress.Tcp(local.getHostString, local.getPort), protocol),
      () => {
        running.set(false)
        if (!socket.isClosed) socket.close()
        acceptor.interrupt()
        acceptor.join()
      },
      () => acceptor.isAlive && !socket.isClosed,
    )
  }

  private def acceptLoop(socket: ServerSocket, running: AtomicBoolean): Unit =
    while (running.get() && !socket.isClosed) {
      try {
        val accepted = socket.accept()
        accepted.close()
      } catch {
        case _: IOException if !running.get() || socket.isClosed => ()
      }
    }
}
