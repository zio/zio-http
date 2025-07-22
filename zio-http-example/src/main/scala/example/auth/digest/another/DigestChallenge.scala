package example.auth.digest.another

import example.auth.digest.another.DigestAuthError.{NonceExpired, ReplayAttack}
import zio.Config.Secret
import zio._
import zio.http._
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

case class DigestChallenge(
  realm: String,
  nonce: String,
  opaque: Option[String] = None,
  algorithm: HashAlgorithm = HashAlgorithm.MD5,
  qop: List[QualityOfProtection] = List(QualityOfProtection.Auth),
  stale: Boolean = false,
  domain: Option[List[String]] = None,
  charset: Option[String] = Some("UTF-8"),
  userhash: Boolean = false,
)

// Updated UserCredentials case class with email field
case class UserCredentials(username: String, password: Secret, email: String)

// JSON codecs for request/response
case class UpdateEmailRequest(email: String)
case class UpdateEmailResponse(message: String, newEmail: String)

object UpdateEmailRequest {
  implicit val decoder: JsonDecoder[UpdateEmailRequest] = DeriveJsonDecoder.gen[UpdateEmailRequest]
}

object UpdateEmailResponse {
  implicit val encoder: JsonEncoder[UpdateEmailResponse] = DeriveJsonEncoder.gen[UpdateEmailResponse]
}

sealed trait HashAlgorithm {
  def name: String
  def digestSize: Int
}

object HashAlgorithm {
  case object MD5 extends HashAlgorithm {
    val name       = "MD5"
    val digestSize = 128
  }

  case object SHA256 extends HashAlgorithm {
    val name       = "SHA-256"
    val digestSize = 256
  }

  case object SHA512 extends HashAlgorithm {
    val name       = "SHA-512"
    val digestSize = 512
  }

  case object SHA256_SESS extends HashAlgorithm {
    val name       = "SHA-256-sess"
    val digestSize = 256
  }

  def fromString(s: String): Option[HashAlgorithm] = s.toLowerCase match {
    case "md5"          => Some(MD5)
    case "sha-256"      => Some(SHA256)
    case "sha-512"      => Some(SHA512)
    case "sha-256-sess" => Some(SHA256_SESS)
    case _              => None
  }
}

sealed trait QualityOfProtection {
  def name: String
}

object QualityOfProtection {
  case object Auth extends QualityOfProtection {
    val name = "auth"
  }

  case object AuthInt extends QualityOfProtection {
    val name = "auth-int"
  }

  def fromString(s: String): Option[QualityOfProtection] = s.toLowerCase match {
    case "auth"     => Some(Auth)
    case "auth-int" => Some(AuthInt)
    case _          => None
  }
}

sealed trait DigestAuthError extends Throwable

object DigestAuthError {
  case class NonceExpired(nonce: String)             extends DigestAuthError {
    override def getMessage: String = s"Nonce expired: $nonce"
  }
  case class ReplayAttack(nonce: String, nc: String) extends DigestAuthError {
    override def getMessage: String = s"Replay attack detected for nonce: $nonce with nc: $nc"
  }
}

trait DigestHashService {
  def hash(data: String, algorithm: HashAlgorithm): Task[String]
  def keyedDigest(secret: String, data: String, algorithm: HashAlgorithm): Task[String]
}

object DigestHashService {
  val live: ULayer[DigestHashService] = ZLayer.succeed(new DigestHashService {

    def hash(data: String, algorithm: HashAlgorithm): Task[String] =
      ZIO.attempt {
        val md = algorithm match {
          case HashAlgorithm.MD5                                =>
            MessageDigest.getInstance("MD5")
          case HashAlgorithm.SHA256 | HashAlgorithm.SHA256_SESS =>
            MessageDigest.getInstance("SHA-256")
          case HashAlgorithm.SHA512                         =>
            MessageDigest.getInstance("SHA-512")
        }
        md.digest(data.getBytes("UTF-8"))
          .map(b => String.format("%02x", b & 0xff))
          .mkString
      }

    def keyedDigest(secret: String, data: String, algorithm: HashAlgorithm): Task[String] =
      hash(s"$secret:$data", algorithm)
  })
}

trait DigestAuthService {
  def createChallenge(realm: String): UIO[List[DigestChallenge]]

  def validateCredentials(response: String)(
    username: String,
    password: Secret,
    method: Method,
    uri: URI,
    realm: String,
    nonce: String,
    algorithm: HashAlgorithm = HashAlgorithm.MD5,
    cnonce: Option[String] = None,
    opaque: Option[String] = None,
    qop: Option[QualityOfProtection] = None,
    nc: Option[String] = None,
    userhash: Boolean = false,
    body: Option[String] = None,
  ): Task[Boolean]

}

object DigestAuthService {
  val live: ZLayer[DigestHashService & NonceService, Nothing, DigestAuthService] =
    ZLayer.fromFunction((hashService: DigestHashService, nonceService: NonceService) =>
      new DigestAuthService {
        def createChallenge(
          realm: String,
        ): UIO[List[DigestChallenge]] =
          for {
          timestamp <- Clock.currentTime(TimeUnit.MILLISECONDS)
          nonce     <- nonceService.generateNonce(timestamp)
          bytes     <- Random.nextString(32).map(_.getBytes(Charsets.Utf8))
          opaque    <- ZIO.succeed(Base64.getEncoder.encodeToString(bytes))
        } yield
           {
             List(HashAlgorithm.SHA256, HashAlgorithm.SHA256_SESS/* HashAlgorithm.MD5, HashAlgorithm.SHA512*/).map { algorithm =>
               DigestChallenge(
                 realm = realm,
                 nonce = nonce,
                 opaque = Some(opaque),
                 algorithm = algorithm,
                 qop = List( QualityOfProtection.AuthInt),
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
        ): Task[String] = {
          val a1 = s"$username:$realm:${password.stringValue}"
          algorithm match {
            // if session algorithm
            case HashAlgorithm.SHA256_SESS =>
              hashService
                .hash(a1, HashAlgorithm.SHA256)
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
        ): Task[String] =
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
        ): Task[String] = {
          val responseData = qop match {
            case Some(_) =>
              val ncValue     = nc.getOrElse("00000001")
              val cnonceValue = cnonce.getOrElse("")
              val qopValue    = qop.map(_.name).getOrElse("")
              s"$nonce:$ncValue:$cnonceValue:$qopValue:$ha2"
            case None    =>
              s"$nonce:$ha2"
          }
          hashService.keyedDigest(ha1, responseData, algorithm)
        }

        def validateCredentials(response: String)(
          username: String,
          password: Secret,
          method: Method,
          uri: URI,
          realm: String,
          nonce: String,
          algorithm: HashAlgorithm = HashAlgorithm.MD5,
          cnonce: Option[String] = None,
          opaque: Option[String] = None,
          qop: Option[QualityOfProtection] = None,
          nc: Option[String] = None,
          userhash: Boolean = false,
          body: Option[String] = None,
        ): Task[Boolean] = {

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
              algorithm
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

object DigestAuthAspect {

  def apply(
    realm: String,
    getUserCredentials: String => Task[Option[UserCredentials]],
  )(implicit trace: Trace): HandlerAspect[DigestAuthService, UserCredentials] = {

    def createUnauthorizedResponse(challenges: List[DigestChallenge]): Response = {
      val headers = challenges.map { challenge =>
        Header.WWWAuthenticate.Digest(
          realm = Some(challenge.realm),
          domain = challenge.domain.flatMap(_.headOption),
          nonce = Some(challenge.nonce),
          stale = Some(challenge.stale),
          opaque = challenge.opaque,
          algorithm = Some(challenge.algorithm.name),
          qop = Some(challenge.qop.map(_.name).mkString(", ")),
          charset = challenge.charset,
          userhash = Some(challenge.userhash)
        )
      }

     val res = headers.foldLeft(Response.status(Status.Unauthorized))((resp, header) =>
        resp.addHeader(header)
      )

      println(res)
     res
    }

    HandlerAspect.interceptIncomingHandler[DigestAuthService, UserCredentials] {
      Handler.fromFunctionZIO[Request](request =>
        request.header(Header.Authorization) match {
          case Some(authHeader: Header.Authorization.Digest) =>
            for {
              digestService <- ZIO.service[DigestAuthService]

              userCredsOption <- getUserCredentials(authHeader.username).orDie
              userCreds       <- userCredsOption match {
                case Some(creds) => ZIO.succeed(creds)
                case None        =>
                  digestService
                    .createChallenge(realm)
                    .flatMap(challenge => ZIO.fail(createUnauthorizedResponse(challenge)))
              }
              entityBody      <- request.body.asString.option

              isValid <- digestService
                .validateCredentials(authHeader.response)(
                  username = authHeader.username,
                  password = userCreds.password,
                  realm = authHeader.realm,
                  nonce = authHeader.nonce,
                  uri = authHeader.uri,
                  algorithm = HashAlgorithm.fromString(authHeader.algorithm).getOrElse(HashAlgorithm.MD5),
                  cnonce = Some(authHeader.cnonce),
                  opaque = Option(authHeader.opaque),
                  qop = QualityOfProtection.fromString(authHeader.qop),
                  nc = Some(String.format("%08d", authHeader.nc)), // Ensure 8-digit zero-padded format
                  userhash = authHeader.userhash,
                  method = request.method,
                  body = entityBody,
                )
                .mapError(e => Response.unauthorized(e.getMessage))

              result <-
                if (isValid)
                  ZIO.succeed((request, userCreds))
                else
                  digestService
                    .createChallenge(authHeader.realm)
                    .flatMap(challenge => ZIO.fail(createUnauthorizedResponse(challenge)))
            } yield result

          case _ =>
            // No auth header or not digest, send challenge
            ZIO.serviceWithZIO[DigestAuthService] {
              _.createChallenge(realm)
                .flatMap(challenge => ZIO.fail(createUnauthorizedResponse(challenge)))
            }
        },
      )

    }
  }
}
