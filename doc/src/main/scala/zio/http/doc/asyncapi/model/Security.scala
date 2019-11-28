package zio.http.doc.asyncapi.model

import java.net.URL

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
