package example.auth.bearer.oauth.core
import zio.json._

case class GitHubTokenResponse(
  access_token: String,
  token_type: String,
  scope: String,
)

object GitHubTokenResponse {
  implicit val decoder: JsonDecoder[GitHubTokenResponse] =
    DeriveJsonDecoder.gen
}
