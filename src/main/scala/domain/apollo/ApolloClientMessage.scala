package zio-http.domain.apollo

import zio-http.domain.apollo.circe.ApolloClientMessageCodec
import zio-http.domain.socket.{ClientMessage, OperationId}
import caliban.GraphQLRequest

/**
 * Messages as per Apollo' subscription protocol Supports automatic json encoding and decoding.
 *
 * Link: https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */

/**
 * Messages received by the server that are sent by the client.
 */
sealed trait ApolloClientMessage { self =>
  def asClientMessage: ClientMessage[GraphQLRequest] = ApolloClientMessage.asClientMessage(self)
}

object ApolloClientMessage extends ApolloClientMessageCodec {

  /**
   * Client sends this message after plain websocket connection to start the communication with the server.
   */
  case object ConnectionInit extends ApolloClientMessage

  /**
   * Client sends this message to execute GraphQL operation
   */
  case class Start(id: OperationId, payload: GraphQLRequest) extends ApolloClientMessage

  /**
   * Client sends this message in order to stop a running GraphQL operation execution (for example: unsubscribe)
   */
  case class Stop(id: OperationId) extends ApolloClientMessage

  /**
   * Client sends this message to terminate the connection.
   */
  case object ConnectionTerminate extends ApolloClientMessage

  def asClientMessage(msg: ApolloClientMessage): ClientMessage[GraphQLRequest] = msg match {
    case ApolloClientMessage.ConnectionInit      => ClientMessage.initialize
    case ApolloClientMessage.Start(id, payload)  => ClientMessage.start(id, payload)
    case ApolloClientMessage.Stop(id)            => ClientMessage.stop(id)
    case ApolloClientMessage.ConnectionTerminate => ClientMessage.terminate
  }
}
