package example.auth.digest.core

sealed trait DigestAuthError extends Throwable

object DigestAuthError {
  case class NonceExpired(nonce: String)                       extends DigestAuthError
  case class InvalidNonce(nonce: String)                       extends DigestAuthError
  case class ReplayAttack(nonce: String, nc: NC)               extends DigestAuthError
  case class InvalidResponse(expected: String, actual: String) extends DigestAuthError
  case class UnsupportedQop(qop: String)                       extends DigestAuthError
  case class MissingRequiredField(field: String)               extends DigestAuthError
  case class UnsupportedAuthHeader(message: String)            extends DigestAuthError
}
