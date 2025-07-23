package example.auth.digest.core

import example.auth.digest.core.DigestAuthError.{NonceExpired, ReplayAttack}
import example.auth.digest.core.QualityOfProtection.Auth
import zio._
import zio.http._

object DigestAuthHandlerAspect {

  def apply(
    realm: String,
    qop: List[QualityOfProtection] = List(Auth),
  ): HandlerAspect[DigestAuthService & UserService, User] = {

    def createUnauthorizedResponse(challenges: List[DigestChallenge], message: Option[String] = None): Response = {
      message
        .map(Response.unauthorized)
        .getOrElse(Response.unauthorized)
        .addHeaders(Headers(challenges.map { challenge =>
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
              userOption    <- ZIO
                .serviceWithZIO[UserService](_.getUser(authHeader.username))
                .mapError(_ => Response.internalServerError(s"Failed to authenticate user ${authHeader.username}"))
              user          <- userOption match {
                case Some(u) => ZIO.succeed(u)
                case None    =>
                  digestService
                    .createChallenge(realm, qop)
                    .flatMap(challenge => ZIO.fail(createUnauthorizedResponse(challenge)))
              }
              qp <- ZIO.fromOption(QualityOfProtection.fromString(authHeader.qop)).orElse(
                digestService
                  .createChallenge(realm, qop).flatMap( challenges =>
                    ZIO.fail(
                      createUnauthorizedResponse(challenges, message = Some(s"Unsupported qop: ${authHeader.qop}"))
                    )
                  )
              )
              entityBody    <- request.body.asString.option
              isValid       <- digestService
                .validateCredentials(authHeader.response)(
                  username = authHeader.username,
                  password = user.password,
                  realm = authHeader.realm,
                  nonce = authHeader.nonce,
                  uri = authHeader.uri,
                  algorithm = HashAlgorithm.fromString(authHeader.algorithm).getOrElse(HashAlgorithm.MD5),
                  cnonce = authHeader.cnonce,
                  opaque = authHeader.opaque,
                  qop = qp,
                  nc = String.format("%08d", authHeader.nc), // Ensure 8-digit zero-padded format
                  userhash = authHeader.userhash,
                  method = request.method,
                  body = entityBody,
                )
                .flatMapError {
                  case NonceExpired(nonce) =>
                  digestService
                    .createChallenge(authHeader.realm, QualityOfProtection.fromString(authHeader.qop).toList)
                    .flatMap(challenge =>
                      ZIO.succeed(
                        createUnauthorizedResponse(
                          challenge,
                          message = Some(s"Nonce expired for user ${authHeader.username}: $nonce"),
                        )
                      ),
                    )
                  case ReplayAttack(nonce, nc) =>
                    digestService
                      .createChallenge(authHeader.realm, QualityOfProtection.fromString(authHeader.qop).toList)
                      .flatMap(challenge =>
                        ZIO.succeed(
                          createUnauthorizedResponse(
                            challenge,
                            message = Some(s"The nonce $nonce with nc $nc has already been used by user ${authHeader.username}.")
                          )
                        ),
                      )

                }

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

}
