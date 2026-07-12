package zio.http

import java.util.concurrent.atomic.AtomicBoolean

sealed abstract class ServerHandle extends AutoCloseable {
  def bindings: List[BoundConnector]
  def isRunning: Boolean
  def shutdown(): Unit
  def awaitShutdown(): Unit

  def shutdownAndWait(): Unit = {
    shutdown()
    awaitShutdown()
  }

  def registerShutdownHook(): this.type = {
    Runtime.getRuntime.addShutdownHook(new Thread(() => shutdownAndWait()))
    this
  }

  override def close(): Unit = shutdownAndWait()
}

case class BoundConnector(address: BoundAddress, protocol: Protocol)

sealed trait BoundAddress
object BoundAddress {
  case class Tcp(host: String, port: Int) extends BoundAddress
  case class Unix(path: java.nio.file.Path) extends BoundAddress
}

private[http] final case class BoundConnectorHandle(
  binding: BoundConnector,
  close0: () => Unit,
  isRunning0: () => Boolean,
)

private[http] object ServerHandle {
  def live(bound: List[BoundConnectorHandle]): ServerHandle =
    new LiveServerHandle(bound)

  private final class LiveServerHandle(bound: List[BoundConnectorHandle]) extends ServerHandle {
    private val closed = new AtomicBoolean(false)

    override def bindings: List[BoundConnector] = bound.map(_.binding)

    override def isRunning: Boolean = !closed.get() && bound.forall(_.isRunning0())

    override def shutdown(): Unit =
      if (closed.compareAndSet(false, true)) bound.foreach(_.close0())

    override def awaitShutdown(): Unit = ()
  }
}
