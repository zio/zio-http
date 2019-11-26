package zio.http.asyncapi.model

case class OAuthFlows(
  `implicit`: OAuthFlow,
  password: OAuthFlow,
  clientCredentials: OAuthFlow,
  authorizationCode: OAuthFlow
)
