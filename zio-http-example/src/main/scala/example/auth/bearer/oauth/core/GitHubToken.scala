package example.auth.bearer.oauth.core
import zio.json._

case class GitHubToken(
  access_token: String,
  token_type: String,
  scope: String,
)

object GitHubToken {
  implicit val decoder: JsonDecoder[GitHubToken] =
    DeriveJsonDecoder.gen
}
