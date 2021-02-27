package zio-http.domain.apollo.circe

import zio-http.domain.apollo.ApolloServerMessage
import zio-http.domain.apollo.ApolloServerMessage.ApolloError
import caliban.{CalibanError, GraphQLResponse}
import io.circe.syntax._
trait ApolloServerMessageCodec {

  private[ApolloServerMessageCodec] object GenericDerivation {
    import io.circe.Encoder
    import zio-http.core.extras._
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.semiauto._

    implicit val config: Configuration =
      Configuration.default.withDiscriminator("type").withSnakeCaseConstructorNames

    implicit val gqlEncoder: Encoder[GraphQLResponse[CalibanError]] =
      GraphQLResponse.circeEncoder[Encoder, CalibanError]

    implicit val error: Encoder[ApolloError] = deriveConfiguredEncoder[ApolloError]

    implicit val encoder: Encoder[ApolloServerMessage] = deriveConfiguredEncoder[ApolloServerMessage]

  }

  import GenericDerivation._

  /**
   * Encodes a GQLServerMessage into a JSON TODO: return a string
   */
  def asString(msg: ApolloServerMessage): String = msg.asJson.noSpaces

}
