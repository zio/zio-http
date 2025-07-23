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

trait DigestAuthService {
  def createChallenge(realm: String, qop: List[QualityOfProtection]): UIO[List[DigestChallenge]]

  def validateCredentials(response: String)(
    username: String,
    password: Secret,
    method: Method,
    uri: URI,
    realm: String,
    nonce: String,
    algorithm: HashAlgorithm = MD5,
    cnonce: Option[String] = None,
    opaque: Option[String] = None,
    qop: Option[QualityOfProtection] = None,
    nc: Option[String] = None,
    userhash: Boolean = false,
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
        ): UIO[List[DigestChallenge]] =
          for {
            timestamp <- Clock.currentTime(TimeUnit.MILLISECONDS)
            nonce     <- nonceService.generateNonce(timestamp)
            bytes     <- Random.nextString(32).map(_.getBytes(Charsets.Utf8))
            opaque    <- ZIO.succeed(Base64.getEncoder.encodeToString(bytes))
          } yield {
            List(MD5, MD5_SESS, SHA256, SHA256_SESS, SHA512, SHA512_SESS).map {
              algorithm =>
                DigestChallenge(
                  realm = realm,
                  nonce = nonce,
                  opaque = Some(opaque),
                  algorithm = algorithm,
                  qop = qop,
                )
            }

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
            case _                         =>
              ZIO.succeed(a1)
          }
        }

        private def calculateA2(
          method: String,
          uri: URI,
          qop: Option[QualityOfProtection],
          entityBody: Option[String],
          algorithm: HashAlgorithm,
        ): UIO[String] =
          qop match {
            case Some(QualityOfProtection.AuthInt) =>
              entityBody match {
                case Some(body) =>
                  for {
                    bodyHash <- hashService.hash(body, algorithm)
                  } yield s"$method:${uri.getPath}:$bodyHash"
                case None       =>
                  ZIO.succeed(s"$method:${uri.getPath}:")
              }
            case _                                 =>
              ZIO.succeed(s"$method:${uri.getPath}")
          }

        private def calculateResponse(
          ha1: String,
          nonce: String,
          nc: Option[String],
          cnonce: Option[String],
          qop: Option[QualityOfProtection],
          ha2: String,
          algorithm: HashAlgorithm,
        ): UIO[String] = {
          val responseData = qop match {
            case Some(_) =>
              val ncValue     = nc.getOrElse("00000001")
              val cnonceValue = cnonce.getOrElse("")
              val qopValue    = qop.map(_.name).getOrElse("")
              s"$nonce:$ncValue:$cnonceValue:$qopValue:$ha2"
            case None    =>
              s"$nonce:$ha2"
          }
          hashService.keyedHash(responseData, algorithm, ha1)
        }

        def validateCredentials(response: String)(
          username: String,
          password: Secret,
          method: Method,
          uri: URI,
          realm: String,
          nonce: String,
          algorithm: HashAlgorithm = MD5,
          cnonce: Option[String] = None,
          opaque: Option[String] = None,
          qop: Option[QualityOfProtection] = None,
          nc: Option[String] = None,
          userhash: Boolean = false,
          body: Option[String] = None,
        ): ZIO[Any, DigestAuthError, Boolean] = {

          for {
            // Validate nonce
            nonceValid <- nonceService.validateNonce(nonce, Duration.fromSeconds(60))
            _          <- ZIO.when(!nonceValid)(ZIO.fail(NonceExpired(nonce)))

            // Check for replay attacks
            ncValue = nc.getOrElse("00000001")
            isUsed <- nonceService.isNonceUsed(nonce, ncValue)
            _      <- ZIO.when(isUsed)(ZIO.fail(ReplayAttack(nonce, ncValue)))

            // Calculate expected response
            a1  <- calculateA1(
              username,
              realm,
              password,
              algorithm,
              nonce,
              cnonce.getOrElse(""),
            )
            ha1 <- hashService.hash(a1, algorithm)

            a2  <- calculateA2(method.name, uri, qop, body, algorithm)
            ha2 <- hashService.hash(a2, algorithm)

            expectedResponse <- calculateResponse(
              ha1,
              nonce,
              nc,
              cnonce,
              qop,
              ha2,
              algorithm,
            )

            // Mark nonce as used
            _ <- nonceService.markNonceUsed(nonce, ncValue)

            // Compare responses
            isValid = expectedResponse.equalsIgnoreCase(response)

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
