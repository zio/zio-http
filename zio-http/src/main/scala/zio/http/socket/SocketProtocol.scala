package zio.http.socket

import io.netty.handler.codec.http.websocketx.{
  WebSocketClientProtocolConfig,
  WebSocketCloseStatus,
  WebSocketServerProtocolConfig,
}
import zio.Duration
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Server side websocket configuration
 */
final case class SocketProtocol(
  subprotocols: Option[String] = None,
  handshakeTimeoutMillis: Long = 10000L,
  forceCloseTimeoutMillis: Long = -1L,
  handleCloseFrames: Boolean = true,
  sendCloseFrame: WebSocketCloseStatus = WebSocketCloseStatus.NORMAL_CLOSURE,
  dropPongFrames: Boolean = true,
  decoderConfig: SocketDecoder = SocketDecoder.default,
) { self =>

  def clientBuilder: WebSocketClientProtocolConfig.Builder = WebSocketClientProtocolConfig
    .newBuilder()
    .subprotocol(subprotocols.orNull)
    .handshakeTimeoutMillis(handshakeTimeoutMillis)
    .forceCloseTimeoutMillis(forceCloseTimeoutMillis)
    .handleCloseFrames(handleCloseFrames)
    .sendCloseFrame(sendCloseFrame)
    .dropPongFrames(dropPongFrames)

  def serverBuilder: WebSocketServerProtocolConfig.Builder = WebSocketServerProtocolConfig
    .newBuilder()
    .checkStartsWith(true)
    .websocketPath("")
    .subprotocols(subprotocols.orNull)
    .handshakeTimeoutMillis(handshakeTimeoutMillis)
    .forceCloseTimeoutMillis(forceCloseTimeoutMillis)
    .handleCloseFrames(handleCloseFrames)
    .sendCloseFrame(sendCloseFrame)
    .dropPongFrames(dropPongFrames)
    .decoderConfig(decoderConfig.javaConfig)

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseFrame(code: Int, reason: String): SocketProtocol =
    self.copy(sendCloseFrame = new WebSocketCloseStatus(code, reason))

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseStatus(status: CloseStatus): SocketProtocol = self.copy(sendCloseFrame = status.asJava)

  def withDecoderConfig(socketDecoder: SocketDecoder): SocketProtocol = self.copy(decoderConfig = socketDecoder)

  /**
   * Close the connection if it was not closed by the client after timeout
   * specified
   */
  def withForceCloseTimeout(duration: Duration): SocketProtocol = self.copy(forceCloseTimeoutMillis = duration.toMillis)

  /**
   * Close frames should be forwarded
   */
  def withForwardCloseFrames(forward: Boolean): SocketProtocol = self.copy(handleCloseFrames = forward)

  /**
   * Pong frames should be forwarded
   */
  def withForwardPongFrames(forward: Boolean): SocketProtocol = self.copy(dropPongFrames = !forward)

  /**
   * Handshake timeout in mills
   */
  def withHandshakeTimeout(duration: Duration): SocketProtocol = self.copy(handshakeTimeoutMillis = duration.toMillis)

  /**
   * Used to specify the websocket sub-protocol
   */
  def withSubProtocol(name: Option[String]): SocketProtocol = self.copy(subprotocols = name)
}

object SocketProtocol {

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketProtocol = SocketProtocol()
}
