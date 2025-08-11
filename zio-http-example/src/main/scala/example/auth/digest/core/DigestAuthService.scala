package example.auth.digest.core

import java.security.MessageDigest
import java.util.Base64

import zio.Config.Secret
import zio._

import zio.http._

import example.auth.digest.core.DigestAlgorithm._
import example.auth.digest.core.DigestAuthError._
import example.auth.digest.core.QualityOfProtection.Auth

trait DigestAuthService {
  def generateChallenge(
    realm: String,
    qop: Set[QualityOfProtection] = Set(Auth),
    algorithm: DigestAlgorithm = MD5,
  ): UIO[DigestChallenge]

  def validateResponse(
    digest: DigestResponse,
    password: Secret,
    method: Method,
    supportedQop: Set[QualityOfProtection],
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
    qop: Set[QualityOfProtection] = Set(Auth),
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
    supportedQop: Set[QualityOfProtection],
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit] = {
    val r = response
    def mapNonceError: NonceError => InvalidResponse = _ => InvalidResponse(r.response)
    for {
      _        <- ZIO.when(!supportedQop.contains(r.qop))(ZIO.fail(UnsupportedQop(r.qop.name)))
      _        <- nonceService.validateNonce(r.nonce, Duration.fromSeconds(NONCE_MAX_AGE)).mapError(mapNonceError)
      _        <- nonceService.isNonceUsed(r.nonce, r.nc).mapError(mapNonceError)
      expected <- digestService.computeResponse(r.username, r.realm, password, r.nonce, r.nc, r.cnonce, r.algorithm, r.qop, r.uri, method, body)
      _        <- isEqual(expected, r.response)
      _        <- nonceService.markNonceUsed(r.nonce, r.nc)
    } yield ()
  }
  // format: on

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
      ZIO.fail(InvalidResponse(actual))
  }
}
