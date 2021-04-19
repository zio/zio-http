package zhttp.socket

import io.netty.handler.codec.http.websocketx.{
  WebSocketCloseStatus => JWebSocketCloseStatus,
  WebSocketServerProtocolConfig => JWebSocketServerProtocolConfig,
}
import zio.duration._

sealed trait ProtocolConfig { self =>
  def asJava: JWebSocketServerProtocolConfig    = ProtocolConfig.asJava(self)
  def ++(other: ProtocolConfig): ProtocolConfig = ProtocolConfig.Concat(self, other)
}

object ProtocolConfig {
  private case object Empty                                        extends ProtocolConfig
  private case class SubProtocol(name: String)                     extends ProtocolConfig
  private case class HandshakeTimeoutMillis(duration: Duration)    extends ProtocolConfig
  private case class ForceCloseTimeoutMillis(duration: Duration)   extends ProtocolConfig
  private case object ForwardCloseFrames                           extends ProtocolConfig
  private case class SendCloseFrame(status: CloseStatus)           extends ProtocolConfig
  private case class SendCloseFrameCode(code: Int, reason: String) extends ProtocolConfig
  private case object ForwardPongFrames                            extends ProtocolConfig
  private case class Concat(a: ProtocolConfig, b: ProtocolConfig)  extends ProtocolConfig

  /**
   * Used to specify the websocket sub-protocol
   */
  def subProtocol(name: String): ProtocolConfig = SubProtocol(name)

  /**
   * Handshake timeout in mills
   */
  def handshakeTimeout(duration: Duration): ProtocolConfig = HandshakeTimeoutMillis(duration)

  /**
   * Close the connection if it was not closed by the client after timeout specified
   */
  def forceCloseTimeout(duration: Duration): ProtocolConfig = ForceCloseTimeoutMillis(duration)

  /**
   * Close frames should be forwarded
   */
  def forwardCloseFrames: ProtocolConfig = ForwardCloseFrames

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(status: CloseStatus): ProtocolConfig = SendCloseFrame(status)

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(code: Int, reason: String): ProtocolConfig = SendCloseFrameCode(code, reason)

  /**
   * Creates an empty protocol config
   */
  def empty: ProtocolConfig = Empty

  /**
   * If pong frames should be forwarded
   */
  def forwardPongFrames: ProtocolConfig = ForwardPongFrames

  private def asJava(config: ProtocolConfig): JWebSocketServerProtocolConfig = {
    val bld = JWebSocketServerProtocolConfig.newBuilder().checkStartsWith(true).websocketPath("")
    def loop(config: ProtocolConfig): Unit = {
      config match {
        case Empty                             => ()
        case SubProtocol(name)                 => bld.subprotocols(name)
        case HandshakeTimeoutMillis(duration)  => bld.handshakeTimeoutMillis(duration.toMillis)
        case ForceCloseTimeoutMillis(duration) => bld.forceCloseTimeoutMillis(duration.toMillis)
        case ForwardCloseFrames                => bld.handleCloseFrames(false)
        case SendCloseFrame(status)            => bld.sendCloseFrame(status.asJava)
        case SendCloseFrameCode(code, reason)  => bld.sendCloseFrame(new JWebSocketCloseStatus(code, reason))
        case ForwardPongFrames                 => bld.dropPongFrames(false)
        case Concat(a, b)                      => loop(a); loop(b)
      }
      ()
    }
    loop(config)
    bld.build()
  }
}
