package zio.http

import zio._

/**
 * Opaque session-token generator.
 *
 * Replaces `ZIO.randomWith(_.nextUUID)`: a UUIDv4 string carries only 122 bits
 * of entropy, below the ASVS 128-bit minimum for session identifiers. Tokens
 * here carry 256 bits (32 bytes from `SecureRandom`, Base64-URL encoded
 * without padding to 43 chars), meeting the OWASP 256-bit recommendation.
 *
 * JVM-only: `java.security.SecureRandom` has no Scala.js equivalent, so this
 * helper lives in `jvm/` sources rather than `shared/`.
 */
object SessionToken {

  val EntropyBytes: Int = 32

  def generate: IO[SessionTokenError, String] =
    ZIO.attempt {
      val r = new java.security.SecureRandom
      val b = new Array[Byte](EntropyBytes)
      r.nextBytes(b)
      java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(b)
    }.refineOrDie { case e => SessionTokenError(e) }

}

final case class SessionTokenError(cause: Throwable) extends Throwable(cause)
