package zio.http.doc.asyncapi.model

import java.net.URL

/*
  Represents the security model used by the operation
 */
final case class Security(
  `type`: SecurityScheme,
  description: Option[String],
  name: String,
  in: String,
  scheme: String,
  bearerFormat: String,
  flows: Option[OAuthFlows],
  openConnectUrl: URL
)
