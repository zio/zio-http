package example.auth.digest.core

import example.auth.digest.core.DigestAlgorithm._
import example.auth.digest.core.DigestAuthError._
import example.auth.digest.core.QualityOfProtection.Auth
import zio.Config.Secret
import zio._
import zio.http._

import java.net.URI
import java.security.MessageDigest
import java.util.Base64

// Represents a digest authentication response from the client
case class DigestResponse(
  response: String,
  username: String,
  realm: String,
  uri: URI,
  opaque: String,
  algorithm: DigestAlgorithm,
  qop: QualityOfProtection,
  cnonce: String,
  nonce: String,
  nc: NC,
  userhash: Boolean,
)

object DigestResponse {
  def fromHeader(digest: Header.Authorization.Digest): DigestResponse = {
    DigestResponse(
      response = digest.response,
      username = digest.username,
      realm = digest.realm,
      uri = digest.uri,
      opaque = digest.opaque,
      algorithm = fromString(digest.algorithm).getOrElse(MD5),
      qop = QualityOfProtection.fromString(digest.qop).getOrElse(Auth),
      cnonce = digest.cnonce,
      nonce = digest.nonce,
      nc = NC(digest.nc),
      userhash = digest.userhash,
    )
  }
}

// Represents a digest authentication challenge sent to the client
case class DigestChallenge(
  realm: String,
  nonce: String,
  opaque: Option[String] = None,
  algorithm: DigestAlgorithm = MD5,
  qop: List[QualityOfProtection] = List(Auth),
  stale: Boolean = false,
  domain: Option[List[String]] = None,
  charset: Option[String] = Some("UTF-8"),
  userhash: Boolean = false,
) {
  def toHeader: Header.WWWAuthenticate.Digest = {
    Header.WWWAuthenticate.Digest(
      realm = Some(realm),
      nonce = Some(nonce),
      opaque = opaque,
      algorithm = Some(algorithm.name),
      qop = Some(qop.map(_.name).mkString(", ")),
      stale = Some(stale),
      domain = domain.flatMap(_.headOption),
      charset = charset,
      userhash = Some(userhash),
    )
  }
}

// Error types for digest authentication failures
sealed trait DigestAuthError

object DigestAuthError {
  case class NonceExpired(nonce: String)                       extends DigestAuthError
  case class InvalidNonce(nonce: String)                       extends DigestAuthError
  case class ReplayAttack(nonce: String, nc: NC)               extends DigestAuthError
  case class InvalidResponse(expected: String, actual: String) extends DigestAuthError
}

trait DigestAuthService {
  def generateChallenge(
    realm: String,
    qop: List[QualityOfProtection] = List(Auth),
    algorithm: DigestAlgorithm = MD5,
  ): UIO[DigestChallenge]

  def validateResponse(
    digest: DigestResponse,
    password: Secret,
    method: Method,
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit]
}

object DigestAuthService {
  val live: ZLayer[NonceService & DigestService, Nothing, DigestAuthService] =
    ZLayer.fromFunction((nonceService: NonceService, digestService: DigestService) =>
      DigestAuthServiceLive(nonceService, digestService),
    )
}

case class DigestAuthServiceLive(
  nonceService: NonceService,
  digestService: DigestService,
) extends DigestAuthService {
  val OPAQUE_BYTES_LENGTH = 16
  val NONCE_MAX_AGE       = 300L // 5 minutes

  def generateChallenge(
    realm: String,
    qop: List[QualityOfProtection] = List(Auth),
    algorithm: DigestAlgorithm = MD5,
  ): UIO[DigestChallenge] =
    for {
      nonce  <- nonceService.generateNonce
      opaque <- generateOpaque
    } yield DigestChallenge(
      realm = realm,
      nonce = nonce,
      opaque = Some(opaque),
      algorithm = algorithm,
      qop = qop,
      charset = Some("UTF-8"),
    )

  // format: off
  def validateResponse(
    response: DigestResponse,
    password: Secret,
    method: Method,
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit] = {
    val r = response
    for {
      _        <- nonceService.validateNonce(r.nonce, Duration.fromSeconds(NONCE_MAX_AGE)).mapError(errorMapper)
      _        <- nonceService.isNonceUsed(r.nonce, r.nc).mapError(errorMapper)
      expected <- digestService.computeResponse(r.username, r.realm, password, r.nonce, r.nc, r.cnonce, r.algorithm, r.qop, r.uri, method, body)
      _        <- isEqual(expected, r.response)
      _        <- nonceService.markNonceUsed(r.nonce, r.nc).mapError(errorMapper)
    } yield ()
  }
  // format: on

  private def errorMapper(error: NonceError): DigestAuthError = error match {
    case NonceError.NonceExpired(nonce)         => DigestAuthError.NonceExpired(nonce)
    case NonceError.NonceAlreadyUsed(nonce, nc) => DigestAuthError.ReplayAttack(nonce, nc)
    case NonceError.InvalidNonce(nonce)         => DigestAuthError.InvalidNonce(nonce)
  }

  // Private helper methods
  private def generateOpaque: UIO[String] =
    Random
      .nextBytes(OPAQUE_BYTES_LENGTH)
      .map(_.toArray)
      .map(Base64.getEncoder.encodeToString)

  private def isEqual(expected: String, actual: String): ZIO[Any, InvalidResponse, Unit] = {
    val exp = expected.getBytes("UTF-8")
    val act = actual.getBytes("UTF-8")
    if (MessageDigest.isEqual(exp, act))
      ZIO.unit
    else
      ZIO.fail(InvalidResponse(expected, actual))
  }
}
