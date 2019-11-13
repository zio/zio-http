package zio.http.model

import java.time.LocalDateTime

// https://tools.ietf.org/html/rfc6265
final case class Cookie(
  value: String,
  domain: Option[String],
  path: Option[String],
  expires: Option[LocalDateTime],
  maxAge: Option[Long],
  secure: Boolean,
  httpOnly: Boolean
)
