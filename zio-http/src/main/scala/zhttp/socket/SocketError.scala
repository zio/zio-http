package zhttp.socket

sealed trait SocketError extends Throwable

object SocketError {
  case object UnknownMessage extends SocketError
  def unknown: SocketError = UnknownMessage
}
