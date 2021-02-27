package zio-http.domain.socket

/**
 * Encodes all the possible client messages that can be received over a websocket connection. They can be of two types
 * viz. Connection and Operation.
 */
sealed trait ClientMessage[+A] extends Product with Serializable

object ClientMessage {

  /**
   * Dictate operations specifically on the connection.
   */
  sealed trait ConnectionMessage extends ClientMessage[Nothing]
  object ConnectionMessage {
    case object Initialize extends ConnectionMessage
    case object Terminate  extends ConnectionMessage
  }

  /**
   * Contains information about the operation that needs to be executed on the server. Operation ID is used as a common
   * identifier between client state and the server state of the operation.
   */
  final case class OperationMessage[A](id: OperationId, operation: Operation[A]) extends ClientMessage[A]

  /**
   * Operation can be of two type: Start or Stop.
   */
  sealed trait Operation[+A] extends Product with Serializable
  object Operation {
    final case class Start[A](req: A) extends Operation[A]
    case object Stop                  extends Operation[Nothing]
  }

  def initialize: ClientMessage[Nothing] =
    ClientMessage.ConnectionMessage.Initialize

  def terminate: ClientMessage[Nothing] =
    ClientMessage.ConnectionMessage.Terminate

  def start[A](id: OperationId, query: A): ClientMessage[A] =
    ClientMessage.OperationMessage(id, Operation.Start(query))

  def stop(id: OperationId): ClientMessage[Nothing] =
    ClientMessage.OperationMessage(id, Operation.Stop)

}
