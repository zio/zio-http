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
            .map(algorithm => ZIO.serviceWithZIO[DigestAuthService](_.generateChallenge(realm, qop, algorithm))),
        )
        .flatMap(challenges =>
          ZIO.fail(
            Response
              .unauthorized(message)
              .addHeaders(Headers(challenges.map(_.toHeader))),
          ),
        )

    HandlerAspect.interceptIncomingHandler[DigestAuthService & UserService, User] {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(digest: Header.Authorization.Digest) =>
            for {
              user   <-
                ZIO
                  .serviceWithZIO[UserService](_.getUser(digest.username))
                  .some
                  .orElse(unauthorizedResponse(s"Failed to authenticate user ${digest.username}"))
              body   <- request.body.asString.option
              result <- ZIO
                .serviceWithZIO[DigestAuthService](
                  _.validateResponse(DigestResponse.fromDigestHeader(digest), user.password, request.method, body),
                )
                .catchAll {
                  case NonceExpired(nonce)                   =>
                    unauthorizedResponse(s"Nonce expired for user ${digest.username}: $nonce")
                  case ReplayAttack(nonce, nc)               =>
                    unauthorizedResponse(
                      s"The nonce $nonce with nc $nc has already been used by user ${digest.username}.",
                    )
                  case DigestAuthError.InvalidResponse(_, _) =>
                    unauthorizedResponse(s"Invalid digest response for user ${digest.username}.")
                }
            } yield (request, user)

          case _ =>
            // No auth header or not digest, send challenges
            unauthorizedResponse(s"Missing Authorization header for realm: $realm")
        }
      }

    }
  }

}
