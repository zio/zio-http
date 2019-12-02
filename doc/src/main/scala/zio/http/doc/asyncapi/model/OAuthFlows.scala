package zio.http.doc.asyncapi.model

/*
  Configuration for OAuth flows
 */
final case class OAuthFlows(
  `implicit`: OAuthFlow,
  password: OAuthFlow,
  clientCredentials: OAuthFlow,
  authorizationCode: OAuthFlow
)
