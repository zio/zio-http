package zhttp.socket

import io.netty.handler.codec.http.websocketx.{WebSocketCloseStatus, WebSocketServerProtocolConfig}
import zio.duration.Duration

/**
 * Server side websocket configuration
 */
sealed trait SocketProtocol { self =>
  import SocketProtocol._
  def ++(other: SocketProtocol): SocketProtocol = SocketProtocol.Concat(self, other)
  def javaConfig: WebSocketServerProtocolConfig = {
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
    b.build()
  }
}

object SocketProtocol {
  private final case class SubProtocol(name: String)                     extends SocketProtocol
  private final case class HandshakeTimeoutMillis(duration: Duration)    extends SocketProtocol
  private final case class ForceCloseTimeoutMillis(duration: Duration)   extends SocketProtocol
  private case object ForwardCloseFrames                                 extends SocketProtocol
  private final case class SendCloseFrame(status: CloseStatus)           extends SocketProtocol
  private final case class SendCloseFrameCode(code: Int, reason: String) extends SocketProtocol
  private case object ForwardPongFrames                                  extends SocketProtocol
  private final case class Concat(a: SocketProtocol, b: SocketProtocol)  extends SocketProtocol
  private case object Default                                            extends SocketProtocol

  /**
   * Used to specify the websocket sub-protocol
   */
  def subProtocol(name: String): SocketProtocol = SubProtocol(name)

  /**
   * Handshake timeout in mills
   */
  def handshakeTimeout(duration: Duration): SocketProtocol =
    HandshakeTimeoutMillis(duration)

  /**
   * Close the connection if it was not closed by the client after timeout specified
   */
  def forceCloseTimeout(duration: Duration): SocketProtocol =
    ForceCloseTimeoutMillis(duration)

  /**
   * Close frames should be forwarded
   */
  def forwardCloseFrames: SocketProtocol = ForwardCloseFrames

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(status: CloseStatus): SocketProtocol = SendCloseFrame(status)

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(code: Int, reason: String): SocketProtocol =
    SendCloseFrameCode(code, reason)

  /**
   * If pong frames should be forwarded
   */
  def forwardPongFrames: SocketProtocol = ForwardPongFrames

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketProtocol = Default
}
