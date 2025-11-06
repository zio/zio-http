package example.auth.bearer.oauth.core

import zio.schema.{DeriveSchema, Schema}

case class GitHubToken(
  access_token: String,
  token_type: String,
  scope: String,
)

object GitHubToken {
  implicit val decoder: Schema[GitHubToken] =
    DeriveSchema.gen
}
