package zio.http.doc.asyncapi.model

/*
  Configuration details of supported OAuth flows
 */
final case class OAuthFlow(
  authorizationUrl: String,
  tokenUrl: String,
  refreshUrl: Option[String],
  scopes: Map[String, String]
)
