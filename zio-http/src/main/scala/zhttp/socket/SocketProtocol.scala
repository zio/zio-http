package zhttp.socket

import io.netty.handler.codec.http.websocketx.{
  WebSocketClientProtocolConfig,
  WebSocketCloseStatus,
  WebSocketServerProtocolConfig,
}
import zio.Duration

/**
 * Server side websocket configuration
 */
sealed trait SocketProtocol { self =>
  import SocketProtocol._

  def clientBuilder: WebSocketClientProtocolConfig.Builder = {
    val b                                    = WebSocketClientProtocolConfig.newBuilder()
    def loop(protocol: SocketProtocol): Unit = {
      protocol match {
        case Default                           => ()
        case SubProtocol(name)                 => b.subprotocol(name)
        case HandshakeTimeoutMillis(duration)  => b.handshakeTimeoutMillis(duration.toMillis)
        case ForceCloseTimeoutMillis(duration) => b.forceCloseTimeoutMillis(duration.toMillis)
        case ForwardCloseFrames                => b.handleCloseFrames(false)
        case SendCloseFrame(status)            => b.sendCloseFrame(status.asJava)
        case SendCloseFrameCode(code, reason)  => b.sendCloseFrame(new WebSocketCloseStatus(code, reason))
        case ForwardPongFrames                 => b.dropPongFrames(false)
        case Concat(a, b)                      =>
          loop(a)
          loop(b)
      }
      ()
    }
    loop(self)
    b
  }

  def serverBuilder: WebSocketServerProtocolConfig.Builder = {
    val b = WebSocketServerProtocolConfig.newBuilder().checkStartsWith(true).websocketPath("")
    def loop(protocol: SocketProtocol): Unit = {
      protocol match {
        case Default                           => ()
        case SubProtocol(name)                 => b.subprotocols(name)
        case HandshakeTimeoutMillis(duration)  => b.handshakeTimeoutMillis(duration.toMillis)
        case ForceCloseTimeoutMillis(duration) => b.forceCloseTimeoutMillis(duration.toMillis)
        case ForwardCloseFrames                => b.handleCloseFrames(false)
        case SendCloseFrame(status)            => b.sendCloseFrame(status.asJava)
        case SendCloseFrameCode(code, reason)  => b.sendCloseFrame(new WebSocketCloseStatus(code, reason))
        case ForwardPongFrames                 => b.dropPongFrames(false)
        case Concat(a, b)                      =>
          loop(a)
          loop(b)
      }
      ()
    }
    loop(self)
    b
  }

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseFrame(status: CloseStatus): SocketProtocol = SocketProtocol.Concat(self, SendCloseFrame(status))

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseFrame(code: Int, reason: String): SocketProtocol =
    SocketProtocol.Concat(self, SendCloseFrameCode(code, reason))

  /**
   * Close the connection if it was not closed by the client after timeout
   * specified
   */
  def withForceCloseTimeout(duration: Duration): SocketProtocol =
    SocketProtocol.Concat(self, ForceCloseTimeoutMillis(duration))

  /**
   * Close frames should be forwarded
   */
  def withForwardCloseFrames: SocketProtocol = SocketProtocol.Concat(self, ForwardCloseFrames)

  /**
   * If pong frames should be forwarded
   */
  def withForwardPongFrames: SocketProtocol = SocketProtocol.Concat(self, ForwardPongFrames)

  /**
   * Handshake timeout in mills
   */
  def withHandshakeTimeout(duration: Duration): SocketProtocol =
    SocketProtocol.Concat(self, HandshakeTimeoutMillis(duration))

  /**
   * Used to specify the websocket sub-protocol
   */
  def withSubProtocol(name: String): SocketProtocol = SocketProtocol.Concat(self, SubProtocol(name))

}

object SocketProtocol {

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketProtocol = Default

  private final case class SubProtocol(name: String) extends SocketProtocol

  private final case class SendCloseFrame(status: CloseStatus) extends SocketProtocol

  private final case class HandshakeTimeoutMillis(duration: Duration) extends SocketProtocol

  private final case class ForceCloseTimeoutMillis(duration: Duration) extends SocketProtocol

  private final case class SendCloseFrameCode(code: Int, reason: String) extends SocketProtocol

  private final case class Concat(a: SocketProtocol, b: SocketProtocol) extends SocketProtocol

  private case object Default extends SocketProtocol

  private case object ForwardPongFrames extends SocketProtocol

  private case object ForwardCloseFrames extends SocketProtocol
}
