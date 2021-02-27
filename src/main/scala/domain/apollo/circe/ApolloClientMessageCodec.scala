package zio-http.domain.apollo.circe

import zio-http.domain.apollo.ApolloClientMessage
import caliban.GraphQLRequest
import io.circe
import io.circe.{Decoder, parser}

trait ApolloClientMessageCodec {
  private[ApolloClientMessageCodec] object GenericDerivation {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.semiauto._

    implicit val config: Configuration =
      Configuration.default.withDiscriminator("type").withSnakeCaseConstructorNames

    implicit val gqlDecoder: Decoder[GraphQLRequest]   = GraphQLRequest.circeDecoder
    implicit val decoder: Decoder[ApolloClientMessage] = deriveConfiguredDecoder[ApolloClientMessage]
  }

  import GenericDerivation._

  /**
   * Decodes a string into a GQLClientMessage
   */
  def decode(string: String): Either[circe.Error, ApolloClientMessage] = parser.decode[ApolloClientMessage](string)
}
