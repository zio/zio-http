package zio-http.domain.socket

sealed trait SocketError extends Throwable

object SocketError {
  case object UnknownMessage extends SocketError
  def unknown: SocketError = UnknownMessage
}
