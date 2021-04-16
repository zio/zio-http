package zhttp.socket

/**
 * Used to represent all the decoding errors inside a socket.
 */
final case class DecodingError(message: WebSocketFrame, description: Option[String] = None) extends Throwable
