package example.auth.digest.core

import example.auth.digest.core.DigestAuthError.{NonceExpired, ReplayAttack}
import example.auth.digest.core.HashAlgorithm._
import example.auth.digest.core.QualityOfProtection.Auth
import zio._
import zio.http._

object DigestAuthHandlerAspect {

  def apply(
    realm: String,
    qop: List[QualityOfProtection] = List(Auth),
    supportedAlgorithms: Set[HashAlgorithm] = Set(MD5, MD5_SESS, SHA256, SHA256_SESS, SHA512, SHA512_SESS),
  ): HandlerAspect[DigestAuthService & UserService, User] = {

    def unauthorizedResponse(message: String): ZIO[DigestAuthService, Response, Nothing] =
      ZIO
        .collectAll(
          supportedAlgorithms
            .map(algorithm => ZIO.serviceWithZIO[DigestAuthService](_.createChallenge(realm, qop, algorithm))),
        )
        .flatMap(challenges =>
          ZIO.fail(
            Response
              .unauthorized(message)
              .addHeaders(
                Headers(
                  challenges.map { challenge =>
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
                  },
                ),
              ),
          ),
        )

    HandlerAspect.interceptIncomingHandler[DigestAuthService & UserService, User] {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(digest: Header.Authorization.Digest) =>
            for {
              user       <-
                ZIO
                  .serviceWithZIO[UserService](_.getUser(digest.username))
                  .some
                  .orElse(unauthorizedResponse(s"Failed to authenticate user ${digest.username}"))
              body <- request.body.asString.option
              result     <- ZIO
                .serviceWithZIO[DigestAuthService](
                  _.validateDigest(DigestHeader.fromDigestHeader(digest), user.password, request.method, body),
                )
                .flatMap {
                  case true  =>
                    ZIO.succeed((request, user))
                  case false =>
                    unauthorizedResponse(s"Invalid digest response for user ${digest.username}.")
                }
                .catchAll {
                  case NonceExpired(nonce)     =>
                    unauthorizedResponse(s"Nonce expired for user ${digest.username}: $nonce")
                  case ReplayAttack(nonce, nc) =>
                    unauthorizedResponse(
                      s"The nonce $nonce with nc $nc has already been used by user ${digest.username}.",
                    )
                }
            } yield result

          case _ =>
            // No auth header or not digest, send challenges
            unauthorizedResponse(s"Missing Authorization header for realm: $realm")
        }
      }

    }
  }

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
  )

}
