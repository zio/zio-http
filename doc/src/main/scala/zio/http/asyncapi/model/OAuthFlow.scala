package zio.http.asyncapi.model

case class OAuthFlow(
  authorizationUrl: String,
  tokenUrl: String,
  refreshUrl: Option[String],
  scopes: Map[String, String]
)
