package zhttp.socket

import io.netty.handler.codec.http.websocketx.{WebSocketDecoderConfig => JWebSocketDecoderConfig}

sealed trait DecoderConfig { self =>
  def ++(other: DecoderConfig): DecoderConfig = DecoderConfig.Concat(self, other)
  def asJava: JWebSocketDecoderConfig         = DecoderConfig.asJava(self)
}

object DecoderConfig {

  private case class MaxFramePayloadLength(length: Int)         extends DecoderConfig
  private case object RejectMaskedFrames                        extends DecoderConfig
  private case object AllowMaskMismatch                         extends DecoderConfig
  private case object AllowExtensions                           extends DecoderConfig
  private case object AllowProtocolViolation                    extends DecoderConfig
  private case object SkipUTF8Validation                        extends DecoderConfig
  private case class Concat(a: DecoderConfig, b: DecoderConfig) extends DecoderConfig
  private case object Empty                                     extends DecoderConfig

  /**
   * Sets Maximum length of a frame's payload. Setting this to an appropriate value for you application helps check for
   * denial of services attacks.
   */
  def maxFramePayloadLength(length: Int): DecoderConfig = MaxFramePayloadLength(length)

  /**
   * Web socket servers must set this to true to reject incoming masked payload.
   */
  def rejectMaskedFrames: DecoderConfig = RejectMaskedFrames

  /**
   * When set to true, frames which are not masked properly according to the standard will still be accepted.
   */
  def allowMaskMismatch: DecoderConfig = AllowMaskMismatch

  /**
   * Allow extensions to be used in the reserved bits of the web socket frame
   */
  def allowExtensions: DecoderConfig = AllowExtensions

  /**
   * Flag to not send close frame immediately on any protocol violation.ion.
   */
  def allowProtocolViolation: DecoderConfig = AllowProtocolViolation

  /**
   * Allows you to avoid adding of Utf8FrameValidator to the pipeline on the WebSocketServerProtocolHandler creation.
   * This is useful (less overhead) when you use only BinaryWebSocketFrame within your web socket connection.
   */
  def skipUTF8Validation: DecoderConfig = SkipUTF8Validation

  /**
   * Creates an empty decoder config
   */
  def empty: DecoderConfig = Empty

  def asJava(config: DecoderConfig): JWebSocketDecoderConfig = {
    val bld = JWebSocketDecoderConfig.newBuilder()

    def loop(config: DecoderConfig): Unit = {
      config match {
        case Empty                         => ()
        case MaxFramePayloadLength(length) => bld.maxFramePayloadLength(length)
        case RejectMaskedFrames            => bld.expectMaskedFrames(false)
        case AllowMaskMismatch             => bld.allowMaskMismatch(true)
        case AllowExtensions               => bld.allowExtensions(true)
        case AllowProtocolViolation        => bld.closeOnProtocolViolation(false)
        case SkipUTF8Validation            => bld.withUTF8Validator(false)
        case Concat(a, b)                  => loop(a); loop(b)
      }
      ()
    }
    loop(config)
    bld.build()
  }
}
