package example.auth.bearer.oauth

import java.time.Clock

import zio.Config.Secret
import zio._

import zio.http._

import example.auth.bearer.oauth.core._

/**
 * OAuth 2.0 Authentication Server using GitHub as the identity provider. This
 * server implements the OAuth 2.0 authorization code flow with GitHub.
 *
 * Setup:
 *   1. Create a GitHub OAuth App at https://github.com/settings/developers
 *   2. Set Authorization callback URL to:
 *      http://localhost:8080/auth/github/callback
 *   3. Replace GH_CLIENT_ID and GH_CLIENT_SECRET with your GitHub app
 *      credentials
 */
object AuthenticationServer extends ZIOAppDefault {
  implicit val clock: Clock = Clock.systemUTC

  override val run = {
    for {
      githubClientId <-
        System
          .env("GH_CLIENT_ID")
          .flatMap(ZIO.fromOption(_))
          .orDieWith(_ => new Exception("GH_CLIENT_ID environment variable not set"))
      githubSecret   <-
        System
          .env("GH_CLIENT_SECRET")
          .flatMap(ZIO.fromOption(_))
          .orDieWith(_ => new Exception("GH_CLIENT_SECRET environment variable not set"))
          .map(Secret(_))
      authService    <- GithubAuthService.make(githubClientId, githubSecret)
    } yield authService
  }.flatMap(authService =>
    Server
      .serve(authService.routes)
      .provide(
        Server.default,
        Client.default,
        JwtTokenService.live(
          secretKey = Secret("secret"),
          accessTokenTTL = 300.seconds,
          refreshTokenTTL = 5.minutes,
        ),
      ),
  )
}
