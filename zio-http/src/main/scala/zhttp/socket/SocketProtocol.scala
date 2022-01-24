package zhttp.socket

import io.netty.handler.codec.http.websocketx.{
  WebSocketClientProtocolConfig,
  WebSocketCloseStatus,
  WebSocketServerProtocolConfig,
}
import zhttp.http.Headers
import zhttp.socket.SocketProtocol.Config.{ClientConfig, ServerConfig}
import zio.duration.Duration

/**
 * Server side websocket configuration
 */
sealed trait SocketProtocol[+A] { self =>
  import SocketProtocol._
  def narrow[A1]: SocketProtocol[A1] = self.asInstanceOf[SocketProtocol[A1]]

  def ++[A1 >: A](other: SocketProtocol[A1]): SocketProtocol[A1] = SocketProtocol.Concat(self, other)

  def serverConfig[A1 >: A](implicit ev: A1 =:= ServerConfig): WebSocketServerProtocolConfig = {
    val b = WebSocketServerProtocolConfig.newBuilder().checkStartsWith(true).websocketPath("")
    def loop(protocol: SocketProtocol[A1]): Unit = {
      protocol match {
        case Default                           => ()
        case SocketUri(_)                      => ()
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

  def clientConfig[A1 >: A](headers: Headers)(implicit ev: A1 =:= ClientConfig): WebSocketClientProtocolConfig = {
    val b = WebSocketClientProtocolConfig.newBuilder().customHeaders(headers.encode)
    def loop(protocol: SocketProtocol[A1]): Unit = {
      protocol match {
        case Default                           => ()
        case SocketUri(uri)                    => b.webSocketUri(uri)
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
    b.build()
  }

}

object SocketProtocol {
  sealed trait Config
  object Config {
    sealed trait ServerConfig extends Config
    sealed trait ClientConfig extends Config
  }

  private case object Default                                                     extends SocketProtocol[Config]
  private case object ForwardPongFrames                                           extends SocketProtocol[Config]
  private case object ForwardCloseFrames                                          extends SocketProtocol[Config]
  private final case class SocketUri(uri: String)                                 extends SocketProtocol[ClientConfig]
  private final case class SubProtocol(name: String)                              extends SocketProtocol[Config]
  private final case class SendCloseFrame(status: CloseStatus)                    extends SocketProtocol[Config]
  private final case class HandshakeTimeoutMillis(duration: Duration)             extends SocketProtocol[Config]
  private final case class ForceCloseTimeoutMillis(duration: Duration)            extends SocketProtocol[Config]
  private final case class SendCloseFrameCode(code: Int, reason: String)          extends SocketProtocol[Config]
  private final case class Concat[+A](a: SocketProtocol[A], b: SocketProtocol[A]) extends SocketProtocol[A]

  /**
   * Used to specify the websocket sub-protocol
   */
  def subProtocol(name: String): SocketProtocol[Config] = SubProtocol(name)

  /**
   * Handshake timeout in mills
   */
  def handshakeTimeout(duration: Duration): SocketProtocol[Config] =
    HandshakeTimeoutMillis(duration)

  /**
   * Close the connection if it was not closed by the client after timeout specified
   */
  def forceCloseTimeout(duration: Duration): SocketProtocol[Config] =
    ForceCloseTimeoutMillis(duration)

  /**
   * Close frames should be forwarded
   */
  def forwardCloseFrames: SocketProtocol[Config] = ForwardCloseFrames

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(status: CloseStatus): SocketProtocol[Config] = SendCloseFrame(status)

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(code: Int, reason: String): SocketProtocol[Config] =
    SendCloseFrameCode(code, reason)

  /**
   * If pong frames should be forwarded
   */
  def forwardPongFrames: SocketProtocol[Config] = ForwardPongFrames

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketProtocol[Config] = Default

  /**
   * Sets WebSocketURI
   */
  def uri(uri: String): SocketProtocol[Config] = SocketUri(uri)
}
