package example.auth.bearer.oauth

import example.auth.bearer.oauth.core._
import zio.Config.Secret
import zio._
import zio.http._

import java.time.Clock

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
      githubClientId <- System.env("GH_CLIENT_ID").map(_.getOrElse("Ov23lifH6xxgP5e6CVWz"))
      githubSecret   <- System
        .env("GH_CLIENT_SECRET")
        .map(_.getOrElse("c2f97cb81ee6d4549453f580e8d06f625a17ea78"))
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
          refreshTokenTTL = 300.seconds
        ),
      ),
  )
}
