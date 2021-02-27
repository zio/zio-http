package zio-http.domain.socket

/**
 * Used to encode all the outgoing subscriptions messages from the graphql server. They are of two types viz. Connection
 * & Operation.
 */
sealed trait ServerMessage[+A] extends Product with Serializable

object ServerMessage {

  /**
   * Used to encode an error inside an ServerMessage.
   */
  final case class Error(message: String, cause: Option[Throwable])

  /**
   * Encodes connection server messages of three types viz. - Acknowledge, Terminate, Reconnect.
   */
  sealed trait ConnectionMessage extends ServerMessage[Nothing]
  object ConnectionMessage {
    final case class ConnectionError(message: Error) extends ConnectionMessage
    case object Acknowledge                          extends ConnectionMessage
    case object Terminate                            extends ConnectionMessage
    case object Reconnect                            extends ConnectionMessage
  }

  /**
   * Encodes operation messages of three types viz. - Success, Failure & Complete.
   */
  final case class OperationMessage[A](id: OperationId, operation: Operation[A]) extends ServerMessage[A]

  sealed trait Operation[+A] extends Product with Serializable
  object Operation {
    final case class Success[A](data: A)     extends Operation[A]
    final case class Failure(message: Error) extends Operation[Nothing]
    case object Complete                     extends Operation[Nothing]
  }

  def success[A](id: OperationId, data: A): ServerMessage[A] =
    ServerMessage.OperationMessage(id, Operation.Success(data))

  def operationError(id: OperationId, message: String, cause: Option[Throwable] = None): ServerMessage[Nothing] =
    ServerMessage.OperationMessage(id, Operation.Failure(Error(message, cause)))

  def acknowledge: ServerMessage[Nothing] =
    ServerMessage.ConnectionMessage.Acknowledge

  def terminate: ServerMessage[Nothing] =
    ServerMessage.ConnectionMessage.Terminate

  def connectionError(message: String, cause: Option[Throwable] = None): ServerMessage[Nothing] =
    ServerMessage.ConnectionMessage.ConnectionError(Error(message, cause))
}
