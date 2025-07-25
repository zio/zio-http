package example.auth.digest.core

import example.auth.digest.core.DigestAuthError._
import example.auth.digest.core.DigestAuthHandlerAspect.DigestChallenge
import example.auth.digest.core.HashAlgorithm._
import zio.Config.Secret
import zio._
import zio.http._

import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

case class DigestHeader(
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

object DigestHeader {
  def fromDigestHeader(digest: Header.Authorization.Digest): DigestHeader = {
    DigestHeader(
      response = digest.response,
      username = digest.username,
      realm = digest.realm,
      uri = digest.uri,
      opaque = digest.opaque,
      algorithm = fromString(digest.algorithm).getOrElse(MD5),
      qop = QualityOfProtection.fromString(digest.qop).getOrElse(QualityOfProtection.Auth),
      cnonce = digest.cnonce,
      nonce = digest.nonce,
      nc = String.format("%08d", digest.nc), // Convert hex to int
      userhash = digest.userhash,
    )
  }
}

trait DigestAuthService {
  def createChallenge(realm: String, qop: List[QualityOfProtection], algorithm: HashAlgorithm): UIO[DigestChallenge]

  def validateDigest(
    digest: DigestHeader,
    password: Secret,
    method: Method,
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Boolean]

}

object DigestAuthService {
  val live: ZLayer[HashService & NonceService, Nothing, DigestAuthService] =
    ZLayer.fromFunction((hashService: HashService, nonceService: NonceService) =>
      new DigestAuthService {
        def createChallenge(
          realm: String,
          qop: List[QualityOfProtection],
          algorithm: HashAlgorithm,
        ): UIO[DigestChallenge] =
          for {
            timestamp <- Clock.currentTime(TimeUnit.MILLISECONDS)
            nonce     <- nonceService.generateNonce(timestamp)
            opaque    <- Random.nextBytes(16).map(_.toArray).map(Base64.getEncoder.encodeToString)
          } yield {
            DigestChallenge(
              realm = realm,
              nonce = nonce,
              opaque = Some(opaque),
              algorithm = algorithm,
              qop = qop,
            )
          }

        private def calculateA1(
          username: String,
          realm: String,
          password: Secret,
          algorithm: HashAlgorithm,
          nonce: String,
          cnonce: String,
        ): UIO[String] = {
          val a1 = s"$username:$realm:${password.stringValue}"
          algorithm match {
            // if session algorithm
            case MD5_SESS | SHA256_SESS | SHA512_SESS =>
              hashService
                .hash(a1, algorithm)
                .map(ha1 => s"$ha1:$nonce:$cnonce")
            // if regular algorithm
            case _                                    =>
              ZIO.succeed(a1)
          }
        }

        private def calculateA2(
          method: String,
          uri: URI,
          qop: QualityOfProtection,
          entityBody: Option[String],
          algorithm: HashAlgorithm,
        ): UIO[String] =
          qop match {
            case QualityOfProtection.AuthInt =>
              entityBody match {
                case Some(body) =>
                  hashService
                    .hash(body, algorithm)
                    .map(hbody => s"$method:${uri.getPath}:$hbody")
                case None       =>
                  ZIO.succeed(s"$method:${uri.getPath}:")
              }
            case _                           =>
              ZIO.succeed(s"$method:${uri.getPath}")
          }

        private def calculateResponse(
          ha1: String,
          nonce: String,
          nc: String,
          cnonce: String,
          qop: QualityOfProtection,
          ha2: String,
          algorithm: HashAlgorithm,
        ): UIO[String] = {
          val responseData = s"$nonce:$nc:$cnonce:${qop.name}:$ha2"
          hashService.keyedHash(responseData, algorithm, ha1)
        }

        def validateDigest(
          digest: DigestHeader,
          password: Secret,
          method: Method,
          body: Option[String] = None,
        ): ZIO[Any, DigestAuthError, Boolean] = {

          for {
            // Validate nonce
            nonceValid <- nonceService.validateNonce(digest.nonce, Duration.fromSeconds(60))
            _          <- ZIO.when(!nonceValid)(ZIO.fail(NonceExpired(digest.nonce)))

            // Check for replay attacks
            isUsed <- nonceService.isNonceUsed(digest.nonce, digest.nc)
            _      <- ZIO.when(isUsed)(ZIO.fail(ReplayAttack(digest.nonce, digest.nc)))

            // Calculate expected response
            a1  <- calculateA1(
              username = digest.username,
              realm = digest.realm,
              password = password,
              algorithm = digest.algorithm,
              nonce = digest.nonce,
              cnonce = digest.cnonce,
            )
            ha1 <- hashService.hash(a1, digest.algorithm)

            a2  <- calculateA2(method.name, digest.uri, digest.qop, body, digest.algorithm)
            ha2 <- hashService.hash(a2, digest.algorithm)

            expectedResponse <- calculateResponse(
              ha1 = ha1,
              nonce = digest.nonce,
              nc = digest.nc,
              cnonce = digest.cnonce,
              qop = digest.qop,
              ha2 = ha2,
              algorithm = digest.algorithm,
            )

            // Mark nonce as used
            _ <- nonceService.markNonceUsed(digest.nonce, digest.nc)

            // Compare responses
            isValid = expectedResponse.equalsIgnoreCase(digest.response)

          } yield isValid
        }
      },
    )
}

sealed trait DigestAuthError {
  def message: String
}

object DigestAuthError {
  case class NonceExpired(nonce: String)             extends DigestAuthError {
    override def message: String = s"Nonce expired: $nonce"
  }
  case class ReplayAttack(nonce: String, nc: String) extends DigestAuthError {
    override def message: String = s"Replay attack detected for nonce: $nonce with nc: $nc"
  }
}
