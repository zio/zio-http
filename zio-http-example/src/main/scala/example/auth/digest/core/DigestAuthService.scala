package example.auth.digest.core

import example.auth.digest.core.DigestAuthError._
import example.auth.digest.core.HashAlgorithm._
import zio.Config.Secret
import zio._
import zio.http._
import zio.stream.ZSink.digest

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
    response: DigestResponse,
    password: Secret,
    method: Method,
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit] = {
    for {
      _                <- validateNonce(response.nonce)
      _                <- checkReplayAttack(response.nonce, response.nc)
      expectedResponse <- calculateResponse(
        response.username,
        response.realm,
        password,
        response.nonce,
        response.nc,
        response.cnonce,
        response.algorithm,
        response.qop,
        response.uri,
        method,
        body,
      )
      _                <- markNonceAsUsed(response.nonce, response.nc)
      _                <- compareResponses(expectedResponse, response.response)
    } yield ()
  }

  // Private helper methods
  private def generateOpaque: UIO[String] =
    Random
      .nextBytes(config.nonceByteLength)
      .map(_.toArray)
      .map(Base64.getEncoder.encodeToString)

  private def validateNonce(nonce: String): ZIO[Any, DigestAuthError, Unit] =
    nonceService.validateNonce(nonce, config.nonceValidityDuration).flatMap { isValid =>
      ZIO
        .unless(isValid) {
          ZIO.fail(NonceExpired(nonce))
        }
        .unit
    }

  private def checkReplayAttack(nonce: String, nc: String): ZIO[Any, DigestAuthError, Unit] =
    nonceService.isNonceUsed(nonce, nc).flatMap { isUsed =>
      ZIO
        .when(isUsed) {
          ZIO.fail(ReplayAttack(nonce, nc))
        }
        .unit
    }

  private def markNonceAsUsed(nonce: String, nc: String): UIO[Unit] =
    nonceService.markNonceUsed(nonce, nc)

  private def compareResponses(expected: String, actual: String): ZIO[Any, InvalidResponse, Unit] = {
    val isValid = expected.equalsIgnoreCase(actual)
    ZIO
      .unless(isValid) {
        ZIO.fail(InvalidResponse(expected, actual))
      }
      .unit
  }

  private def calculateResponse(
    username: String,
    realm: String,
    password: Secret,
    nonce: String,
    nc: String,
    cnonce: String,
    algorithm: HashAlgorithm,
    qop: QualityOfProtection,
    uri: URI,
    method: Method,
    body: Option[String],
  ): UIO[String] = {
    for {
      a1       <- calculateA1(username, realm, password, nonce, cnonce, algorithm)
      ha1      <- hashService.hash(a1, algorithm)
      a2       <- calculateA2(method, uri, algorithm, qop, body)
      ha2      <- hashService.hash(a2, algorithm)
      response <- calculateFinalResponse(ha1, ha2, nonce, nc, cnonce, qop, algorithm)
    } yield response
  }

  private def calculateA1(
    username: String,
    realm: String,
    password: Secret,
    nonce: String,
    cnonce: String,
    algorithm: HashAlgorithm,
  ): UIO[String] = {
    val baseA1 = s"$username:$realm:${password.stringValue}"

    algorithm match {
      case MD5_SESS | SHA256_SESS | SHA512_SESS =>
        hashService
          .hash(baseA1, algorithm)
          .map(ha1 => s"$ha1:$nonce:$cnonce")
      case _                                    =>
        ZIO.succeed(baseA1)
    }
  }

  private def calculateA2(
    method: Method,
    uri: URI,
    algorithm: HashAlgorithm,
    qop: QualityOfProtection,
    entityBody: Option[String],
  ): UIO[String] = {
    qop match {
      case QualityOfProtection.AuthInt =>
        entityBody match {
          case Some(body) =>
            hashService
              .hash(body, algorithm)
              .map(hbody => s"${method.name}:${uri.getPath}:$hbody")
          case None       =>
            ZIO.succeed(s"${method.name}:${uri.getPath}:")
        }
      case _                           =>
        ZIO.succeed(s"${method.name}:${uri.getPath}")
    }
  }

  private def calculateFinalResponse(
    ha1: String,
    ha2: String,
    nonce: String,
    nc: String,
    cnonce: String,
    qop: QualityOfProtection,
    algorithm: HashAlgorithm,
  ): UIO[String] = {
    val responseData = s"$nonce:$nc:$cnonce:${qop.name}:$ha2"
    hashService.keyedHash(responseData, algorithm, ha1)
  }
}
