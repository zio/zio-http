package example.auth.digest.core

import example.auth.digest.core.DigestAuthError._
import example.auth.digest.core.HashAlgorithm._
import zio.Config.Secret
import zio._
import zio.http._

import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

// Configuration for digest authentication
case class DigestAuthConfig(
  nonceValidityDuration: Duration = Duration.fromSeconds(12000),
  defaultCharset: String = "UTF-8",
  nonceByteLength: Int = 16,
)

// Represents a digest authentication response from the client
case class DigestResponse(
  response: String,
  username: String,
  realm: String,
  uri: URI,
  opaque: String,
  algorithm: HashAlgorithm,
  qop: QualityOfProtection,
  cnonce: String,
  nonce: String,
  nc: String,
  userhash: Boolean,
)

object DigestResponse {
  def fromDigestHeader(digest: Header.Authorization.Digest): DigestResponse = {
    DigestResponse(
      response = digest.response,
      username = digest.username,
      realm = digest.realm,
      uri = digest.uri,
      opaque = digest.opaque,
      algorithm = fromString(digest.algorithm).getOrElse(MD5),
      qop = QualityOfProtection.fromString(digest.qop).getOrElse(QualityOfProtection.Auth),
      cnonce = digest.cnonce,
      nonce = digest.nonce,
      nc = String.format("%08d", digest.nc), // Format as zero-padded 8-digit string
      userhash = digest.userhash,
    )
  }
}

// Represents a digest authentication challenge sent to the client
case class DigestChallenge(
  realm: String,
  nonce: String,
  opaque: Option[String] = None,
  algorithm: HashAlgorithm = MD5,
  qop: List[QualityOfProtection] = List(QualityOfProtection.Auth),
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
sealed trait DigestAuthError {
  def message: String
}

object DigestAuthError {
  case class NonceExpired(nonce: String) extends DigestAuthError {
    override def message: String = s"Nonce expired: $nonce"
  }

  case class ReplayAttack(nonce: String, nc: String) extends DigestAuthError {
    override def message: String = s"Replay attack detected for nonce: $nonce with nc: $nc"
  }

  case class InvalidResponse(expected: String, actual: String) extends DigestAuthError {
    override def message: String = s"Invalid digest response: expected $expected, got $actual"
  }
}

trait DigestAuthService {
  def generateChallenge(
    realm: String,
    qop: List[QualityOfProtection] = List(QualityOfProtection.Auth),
    algorithm: HashAlgorithm = MD5,
  ): UIO[DigestChallenge]

  def validateResponse(
    digest: DigestResponse,
    password: Secret,
    method: Method,
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit]
}

object DigestAuthService {
  val live: ZLayer[HashService & NonceService & DigestAuthConfig, Nothing, DigestAuthService] =
    ZLayer.fromFunction((hashService: HashService, nonceService: NonceService, config: DigestAuthConfig) =>
      DigestAuthServiceLive(hashService, nonceService, config),
    )

  // Convenience layer with default config
  val liveWithDefaults: ZLayer[HashService & NonceService, Nothing, DigestAuthService] =
    ZLayer.succeed(DigestAuthConfig()) >>> live
}

case class DigestAuthServiceLive(
  hashService: HashService,
  nonceService: NonceService,
  config: DigestAuthConfig,
) extends DigestAuthService {

  def generateChallenge(
    realm: String,
    qop: List[QualityOfProtection] = List(QualityOfProtection.Auth),
    algorithm: HashAlgorithm = MD5,
  ): UIO[DigestChallenge] =
    for {
      timestamp <- Clock.currentTime(TimeUnit.MILLISECONDS)
      nonce     <- nonceService.generateNonce(timestamp)
      opaque    <- generateOpaque
    } yield DigestChallenge(
      realm = realm,
      nonce = nonce,
      opaque = Some(opaque),
      algorithm = algorithm,
      qop = qop,
      charset = Some(config.defaultCharset),
    )

  def validateResponse(
    digest: DigestResponse,
    password: Secret,
    method: Method,
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit] = {
    for {
      _                <- validateNonce(digest)
      _                <- checkReplayAttack(digest)
      expectedResponse <- calculateExpectedResponse(digest, password, method, body)
      _                <- markNonceAsUsed(digest)
      _                <- compareResponses(expectedResponse, digest.response)
    } yield ()
  }

  // Private helper methods

  private def generateOpaque: UIO[String] =
    Random
      .nextBytes(config.nonceByteLength)
      .map(_.toArray)
      .map(Base64.getEncoder.encodeToString)

  private def validateNonce(digest: DigestResponse): ZIO[Any, DigestAuthError, Unit] =
    nonceService.validateNonce(digest.nonce, config.nonceValidityDuration).flatMap { isValid =>
      if (isValid) ZIO.unit
      else ZIO.fail(NonceExpired(digest.nonce))
    }

  private def checkReplayAttack(digest: DigestResponse): ZIO[Any, DigestAuthError, Unit] =
    nonceService.isNonceUsed(digest.nonce, digest.nc).flatMap { isUsed =>
      if (isUsed) ZIO.fail(ReplayAttack(digest.nonce, digest.nc))
      else ZIO.unit
    }

  private def markNonceAsUsed(digest: DigestResponse): UIO[Unit] =
    nonceService.markNonceUsed(digest.nonce, digest.nc)

  private def compareResponses(expected: String, actual: String): ZIO[Any, InvalidResponse, Unit] = {
    val isValid = expected.equalsIgnoreCase(actual)
    if (isValid) ZIO.succeed(())
    else ZIO.fail(InvalidResponse(expected, actual))
  }

  private def calculateExpectedResponse(
    digest: DigestResponse,
    password: Secret,
    method: Method,
    body: Option[String],
  ): UIO[String] = {
    for {
      a1       <- calculateA1(digest, password)
      ha1      <- hashService.hash(a1, digest.algorithm)
      a2       <- calculateA2(method, digest, body)
      ha2      <- hashService.hash(a2, digest.algorithm)
      response <- calculateFinalResponse(ha1, ha2, digest)
    } yield response
  }

  private def calculateA1(digest: DigestResponse, password: Secret): UIO[String] = {
    val baseA1 = s"${digest.username}:${digest.realm}:${password.stringValue}"

    digest.algorithm match {
      case MD5_SESS | SHA256_SESS | SHA512_SESS =>
        hashService
          .hash(baseA1, digest.algorithm)
          .map(ha1 => s"$ha1:${digest.nonce}:${digest.cnonce}")
      case _                                    =>
        ZIO.succeed(baseA1)
    }
  }

  private def calculateA2(
    method: Method,
    digest: DigestResponse,
    entityBody: Option[String],
  ): UIO[String] = {
    digest.qop match {
      case QualityOfProtection.AuthInt =>
        calculateA2WithEntityBody(method, digest, entityBody)
      case _                           =>
        ZIO.succeed(s"${method.name}:${digest.uri.getPath}")
    }
  }

  private def calculateA2WithEntityBody(
    method: Method,
    digest: DigestResponse,
    entityBody: Option[String],
  ): UIO[String] = {
    entityBody match {
      case Some(body) =>
        hashService
          .hash(body, digest.algorithm)
          .map(hbody => s"${method.name}:${digest.uri.getPath}:$hbody")
      case None       =>
        ZIO.succeed(s"${method.name}:${digest.uri.getPath}:")
    }
  }

  private def calculateFinalResponse(
    ha1: String,
    ha2: String,
    digest: DigestResponse,
  ): UIO[String] = {
    val responseData = s"${digest.nonce}:${digest.nc}:${digest.cnonce}:${digest.qop.name}:$ha2"
    hashService.keyedHash(responseData, digest.algorithm, ha1)
  }
}
