package example.auth.bearer.oauth.core

import zio.schema.{DeriveSchema, Schema}

case class Token(
  accessToken: String,
  refreshToken: String,
  tokenType: String = "Bearer",
  expiresIn: Long = 300L,
)

object Token {
  implicit val schema: Schema[Token] = DeriveSchema.gen
}
