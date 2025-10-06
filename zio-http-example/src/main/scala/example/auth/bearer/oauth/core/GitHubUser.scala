package example.auth.bearer.oauth.core

import zio.schema.{DeriveSchema, Schema}

case class GitHubUser(
  id: Long,
  login: String,
  name: Option[String],
  email: Option[String],
  avatar_url: String,
)

object GitHubUser {
  implicit val codec: Schema[GitHubUser] = DeriveSchema.gen
}
