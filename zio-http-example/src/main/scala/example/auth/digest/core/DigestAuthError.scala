package example.auth.digest.core

sealed trait DigestAuthError extends Throwable

object DigestAuthError {
  case class InvalidResponse(response: String)         extends DigestAuthError
  case class UnsupportedQop(qop: String)               extends DigestAuthError
  case class MissingRequiredField(field: String)       extends DigestAuthError
  case class UnsupportedAuthHeader(message: String)    extends DigestAuthError
}
