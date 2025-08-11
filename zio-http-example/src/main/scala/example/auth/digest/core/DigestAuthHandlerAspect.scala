package example.auth.digest.core

import zio._

import zio.http._

import example.auth.digest.core.DigestAlgorithm._
import example.auth.digest.core.QualityOfProtection.Auth

object DigestAuthHandlerAspect {

  def apply(
    realm: String,
    qop: Set[QualityOfProtection] = Set(Auth),
    supportedAlgorithms: Set[DigestAlgorithm] = Set(MD5, MD5_SESS, SHA256, SHA256_SESS, SHA512, SHA512_SESS),
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
            {
              for {
                user <-
                  ZIO
                    .serviceWithZIO[UserService](_.getUser(digest.username))
                body <- request.body.asString.option
                _    <- ZIO
                  .serviceWithZIO[DigestAuthService](
                    _.validateResponse(DigestResponse.fromHeader(digest), user.password, request.method, qop, body),
                  )
              } yield (request, user)
            }.catchAll(_ => unauthorizedResponse("Authentication failed!"))

          case _ =>
            unauthorizedResponse(s"Missing Authorization header for realm: $realm")
        }
      }

    }
  }

}
