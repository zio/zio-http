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
final case class SocketProtocol(
  subprotocols: String = null,
  handshakeTimeoutMillis: Long = 10000L,
  forceCloseTimeoutMillis: Long = -1L,
  handleCloseFrames: Boolean = true,
  sendCloseFrame: WebSocketCloseStatus = WebSocketCloseStatus.NORMAL_CLOSURE,
  dropPongFrames: Boolean = true,
  decoderConfig: SocketDecoder = SocketDecoder.default,
) { self =>

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseFrame(status: CloseStatus): SocketProtocol = self.copy(sendCloseFrame = status.asJava)

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseFrame(code: Int, reason: String): SocketProtocol =
    self.copy(sendCloseFrame = new WebSocketCloseStatus(code, reason))

  /**
   * Close the connection if it was not closed by the client after timeout
   * specified
   */
  def withForceCloseTimeout(duration: Duration): SocketProtocol = self.copy(forceCloseTimeoutMillis = duration.toMillis)

  /**
   * Close frames should be forwarded
   */
  def withForwardCloseFrames: SocketProtocol = self.copy(handleCloseFrames = true)

  /**
   * Close frames should be dropped
   */
  def withDropCloseFrames: SocketProtocol = self.copy(handleCloseFrames = false)

  /**
   * Pong frames should be forwarded
   */
  def withForwardPongFrames: SocketProtocol = self.copy(dropPongFrames = false)

  /**
   * Pong frames should be dropped
   */
  def withDropPongFrames: SocketProtocol = self.copy(dropPongFrames = true)

  /**
   * Handshake timeout in mills
   */
  def withHandshakeTimeout(duration: Duration): SocketProtocol = self.copy(handshakeTimeoutMillis = duration.toMillis)

  /**
   * Used to specify the websocket sub-protocol
   */
  def withSubProtocol(name: String): SocketProtocol = self.copy(subprotocols = name)

  def withDecoderConfig(socketDecoder: SocketDecoder) = self.copy(decoderConfig = socketDecoder)

  def clientBuilder: WebSocketClientProtocolConfig.Builder = WebSocketClientProtocolConfig
    .newBuilder()
    .subprotocol(subprotocols)
    .handshakeTimeoutMillis(handshakeTimeoutMillis)
    .forceCloseTimeoutMillis(forceCloseTimeoutMillis)
    .handleCloseFrames(handleCloseFrames)
    .sendCloseFrame(sendCloseFrame)
    .dropPongFrames(dropPongFrames)
  def serverBuilder: WebSocketServerProtocolConfig.Builder = WebSocketServerProtocolConfig
    .newBuilder()
    .checkStartsWith(true)
    .websocketPath("")
    .subprotocols(subprotocols)
    .handshakeTimeoutMillis(handshakeTimeoutMillis)
    .forceCloseTimeoutMillis(forceCloseTimeoutMillis)
    .handleCloseFrames(handleCloseFrames)
    .sendCloseFrame(sendCloseFrame)
    .dropPongFrames(dropPongFrames)
    .decoderConfig(decoderConfig.javaConfig)
}

object SocketProtocol {

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketProtocol = SocketProtocol()
}
