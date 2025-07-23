package example.auth.digest.core

import example.auth.digest.core.QualityOfProtection.{Auth, AuthInt}
import zio.Config.Secret
import zio._
import zio.http._

object DigestAuthHandlerAspect {

  def apply(
    realm: String,
    qop: List[QualityOfProtection] = List(Auth),
  ): HandlerAspect[DigestAuthService & UserService, User] = {

    def createUnauthorizedResponse(challenges: List[DigestChallenge]): Response = {
      Response.unauthorized.addHeaders(Headers(challenges.map { challenge =>
        Header.WWWAuthenticate.Digest(
          realm = Some(challenge.realm),
          domain = challenge.domain.flatMap(_.headOption),
          nonce = Some(challenge.nonce),
          stale = Some(challenge.stale),
          opaque = challenge.opaque,
          algorithm = Some(challenge.algorithm.name),
          qop = Some(challenge.qop.map(_.name).mkString(", ")),
          charset = challenge.charset,
          userhash = Some(challenge.userhash),
        )
      }))
    }

    HandlerAspect.interceptIncomingHandler[DigestAuthService & UserService, User] {
      Handler.fromFunctionZIO[Request](request =>
        request.header(Header.Authorization) match {
          case Some(authHeader: Header.Authorization.Digest) =>
            for {
              digestService <- ZIO.service[DigestAuthService]
              userOption    <- ZIO.serviceWithZIO[UserService](_.getUser(authHeader.username)).orDie
              user          <- userOption match {
                case Some(u) => ZIO.succeed(u)
                case None    =>
                  digestService
                    .createChallenge(realm, qop)
                    .flatMap(challenge => ZIO.fail(createUnauthorizedResponse(challenge)))
              }
              entityBody    <- request.body.asString.option
              isValid       <- digestService
                .validateCredentials(authHeader.response)(
                  username = authHeader.username,
                  password = user.password,
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
                  ZIO.succeed((request, user))
                else
                  digestService
                    .createChallenge(authHeader.realm, QualityOfProtection.fromString(authHeader.qop).toList)
                    .flatMap(challenge => ZIO.fail(createUnauthorizedResponse(challenge)))
            } yield result

          case _ =>
            // No auth header or not digest, send challenge
            ZIO.serviceWithZIO[DigestAuthService] {
              _.createChallenge(realm, qop)
                .flatMap(challenge => ZIO.fail(createUnauthorizedResponse(challenge)))
            }
        },
      )

    }
  }

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

  case class UserCredentials(username: Username, password: Secret)
  case class Username(value: String)

}
