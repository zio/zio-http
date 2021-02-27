package zio-http.domain.apollo

import zio-http.core.OperationId
import zio-http.domain.apollo.circe.ApolloServerMessageCodec
import zio-http.domain.socket.ServerMessage
import zio-http.domain.socket.ServerMessage.{ConnectionMessage, Operation}
import caliban.{CalibanError, GraphQLResponse}

/**
 * Messages as per Apollo' subscription protocol Supports automatic json encoding and decoding.
 *
 * Link: https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */

sealed trait ApolloServerMessage { self =>
  def asString: String = ApolloServerMessage.asString(self)
}

/**
 * Messages sent to the client by the server.
 */

object ApolloServerMessage extends ApolloServerMessageCodec {

  /**
   * Used to signal all kinds of server error.
   */
  case class ApolloError(message: String, cause: Option[Throwable])
  object ApolloError {
    def apply(error: ServerMessage.Error): ApolloError = ApolloError(error.message, error.cause)
    def apply(message: String): ApolloError            = ApolloError(message, None)
  }

  /**
   * The server may respond with this message to the GQL_CONNECTION_INIT from client, indicating that the server
   * rejected the connection.
   */
  case class ConnectionError(payload: ApolloError) extends ApolloServerMessage
  object ConnectionError {
    def apply(message: String): ConnectionError = ConnectionError(ApolloError(message))
  }

  /**
   * The server may respond with this message to the GQL_CONNECTION_INIT from client, indicating the server accepted the
   * connection.
   */
  case object ConnectionAck extends ApolloServerMessage

  /**
   * The server sends this message to transfer the GraphQL execution result from the server to the client, this message
   * is a response for GQL_START message.
   */
  case class Data(id: OperationId, payload: GraphQLResponse[CalibanError]) extends ApolloServerMessage

  /**
   * Server sends this message upon a failing operation, before the GraphQL execution, usually due to GraphQL validation
   * errors (resolver errors are part of GQL_DATA message, and will be added as errors array)
   */
  case class Error(id: OperationId, errors: List[String]) extends ApolloServerMessage

  /**
   * Server sends this message to indicate that the GraphQL operation is done, and no more data will arrive for the
   * specific operation.
   */
  case class Complete(id: OperationId) extends ApolloServerMessage

  /**
   * Server message that should be sent right after each GQL_CONNECTION_ACK processed and then periodically to keep the
   * client connection alive.
   */
  case object ConnectionKeepAlive extends ApolloServerMessage

  object Error {
    def apply(id: OperationId, cause: Throwable, message: String): ApolloServerMessage.Error =
      Error(id, List[String](message, Option(cause.getMessage).getOrElse("(unknown operation error)")))
  }

  /**
   * Used to Convert a ServerMessage to GQLServerMessage.
   */
  def fromServerMessage(sm: ServerMessage[GraphQLResponse[CalibanError]]): Option[ApolloServerMessage] = sm match {
    case ConnectionMessage.ConnectionError(message) =>
      Option(ConnectionError(ApolloError(message)))

    case ConnectionMessage.Acknowledge => Option(ConnectionAck)

    case ServerMessage.OperationMessage(id, Operation.Success(data)) =>
      Option(Data(id, data))

    case ServerMessage.OperationMessage(id, Operation.Failure(error)) =>
      Option(Error(id, List(error.message)))

    case ServerMessage.OperationMessage(id, Operation.Complete) => Option(Complete(id))

    case _ => Option.empty
  }
}
