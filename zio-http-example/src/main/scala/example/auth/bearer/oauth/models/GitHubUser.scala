package example.auth.bearer.oauth.models

import zio.json.{DeriveJsonCodec, JsonCodec}

case class GitHubUser(
  id: Long,
  login: String,
  name: Option[String],
  email: Option[String],
  avatar_url: String,
)

object GitHubUser {
  implicit val codec: JsonCodec[GitHubUser] = DeriveJsonCodec.gen
}
