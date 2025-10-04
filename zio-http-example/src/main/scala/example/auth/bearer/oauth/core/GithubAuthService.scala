package example.auth.bearer.oauth.core

import java.net.URI
import java.time.Clock

import zio.Config.Secret
import zio._

import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

import zio.http._

/**
 * Authentication Service that handles OAuth flow and JWT token management
 */
class GithubAuthService private (
  private val redirectUris: Ref[Map[String, URI]], // state -> redirectUri
  private val users: Ref[Map[String, GitHubUser]], // userId -> GitHubUser
  private val clientID: String,
  private val clientSecret: Secret,
) {

  // GitHub OAuth Configuration
  private val REDIRECT_URI         = "http://localhost:8080/auth/github/callback"
  private val GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
  private val GITHUB_TOKEN_URL     = "https://github.com/login/oauth/access_token"
  private val GITHUB_USER_API      = "https://api.github.com/user"

  implicit val clock: Clock = Clock.systemUTC

  def routes: Routes[JwtTokenService with Client, Response] =
    Routes(
      Method.GET / Root -> Handler.fromResource("oauth-client.html"),

      // Protected route - requires valid JWT token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.service[UserInfo].flatMap { userInfo =>
          users.get.map {
            _.get(userInfo.username) match {
              case Some(user) =>
                Response(body = Body.from(user))
              case None       =>
                Response(
                  status = Status.NotFound,
                  body = Body.from(
                    Map(
                      "userId"  -> userInfo.username,
                      "message" -> "User profile not found",
                    ),
                  ),
                )
            }
          }
        }
      } @@ AuthMiddleware.bearerAuth(realm = "User Profile"),

      // Start OAuth flow - redirect to GitHub
      Method.GET / "auth" / "github" -> handler { (request: Request) =>
        for {
          state         <- generateRandomString()
          redirectUri   <- ZIO
            .fromOption(request.url.queryParams.queryParams("redirect_uri").headOption)
            .orElseFail(
              Response.badRequest("Missing redirect_uri parameter"),
            )
          _             <- redirectUris.update(_.updated(state, URI.create(redirectUri)))
          githubAuthUrl <- ZIO
            .fromEither(
              URL.decode(
                s"$GITHUB_AUTHORIZE_URL" +
                  s"?client_id=$clientID" +
                  s"&redirect_uri=$REDIRECT_URI" +
                  s"&scope=user:email" +
                  s"&state=$state",
              ),
            )
        } yield Response.status(Status.Found).addHeader(Header.Location(githubAuthUrl))
      },

      // OAuth callback - handle GitHub callback
      Method.GET / "auth" / "github" / "callback" -> handler { (request: Request) =>
        val queryParams = request.url.queryParams
        // Authorization code returned by GitHub after successful user authentication
        // This temporary code is exchanged for an access token
        val code        = queryParams.queryParams("code").headOption
        // The same random string your server generated and sent to GitHub initially
        // prevents malicious sites from initiating fake OAuth flows
        // Your server must verify this matches the state it originally sent
        val state       = queryParams.queryParams("state").headOption
        // Error code indicating why the OAuth flow failed
        val error       = queryParams.queryParams("error").headOption

        error match {
          case Some(err) =>
            ZIO.succeed(Response.unauthorized(s"OAuth error: $err"))
          case None      =>
            (code, state) match {
              case (Some(authCode), Some(stateParam)) =>
                for {
                  uri         <- redirectUris.get.map(_.get(stateParam))
                  redirectUri <- ZIO.fromOption(uri)

                  // Exchange code for access token
                  githubTokens <- exchangeCodeForToken(authCode)

                  // Fetch user info from GitHub
                  githubUser <- fetchGitHubUser(githubTokens.access_token)

                  // Generate our own JWT tokens
                  token <- ZIO.serviceWithZIO[JwtTokenService](
                    _.issueTokens(githubUser.id.toString, githubUser.email.getOrElse(""), Set("user")),
                  )

                  // Clean up state, store user info, and store refresh token
                  _ <- redirectUris.update(_ - stateParam)
                  _ <- users.update(_.updated(githubUser.id.toString, githubUser))

                  // Redirect back to the client with tokens
                  redirectUrl <-
                    ZIO
                      .fromEither(
                        URL.decode(
                          s"$redirectUri?access_token=${token.accessToken}" +
                            s"&refresh_token=${token.refreshToken}" +
                            s"&token_type=${"Bearer"}" +
                            s"&expires_in=${token.expiresIn}",
                        ),
                      )
                } yield Response.status(Status.Found).addHeader(Header.Location(redirectUrl))

              case _ =>
                ZIO.succeed(Response.badRequest("Missing code or state parameter"))
            }
        }
      },

      // Refresh token endpoint
      Method.POST / "refresh" -> handler { (request: Request) =>
        for {
          refreshRequest <- request.body
            .to[Map[String, String]]
            .orElseFail(Response.badRequest("Failed to read request body"))
          refreshToken   <- ZIO
            .fromOption(refreshRequest.get("refreshToken"))
            .orElseFail(Response.badRequest("Missing refresh token"))
          token          <- ZIO
            .serviceWithZIO[JwtTokenService](_.refreshTokens(refreshToken))

        } yield Response(body = Body.from(token))
      },
      Method.POST / "logout"  ->
        handler { (request: Request) =>
          for {
            form         <- request.body.asURLEncodedForm.orElseFail(Response.badRequest("Expected form-encoded data"))
            refreshToken <- ZIO
              .fromOption(form.get("refreshToken").flatMap(_.stringValue))
              .orElseFail(Response.badRequest("Missing refreshToken"))
            tokenService <- ZIO.service[JwtTokenService]
            _            <- tokenService.revokeRefreshToken(refreshToken)
          } yield Response.text("Logged out successfully")
        },
    ).sandbox @@ Middleware.debug

  private def generateRandomString(): ZIO[Any, Nothing, String] = {
    ZIO.succeed {
      val bytes = new Array[Byte](32) // 256 bits
      java.security.SecureRandom.getInstanceStrong.nextBytes(bytes)
      bytes.map("%02x".format(_)).mkString
    }
  }

  private def exchangeCodeForToken(code: String): ZIO[Client, Throwable, GitHubToken] = {
    for {
      response      <- ZClient.batched(
        Request
          .post(
            GITHUB_TOKEN_URL,
            Body.from(
              Map(
                "client_id"     -> clientID,
                "client_secret" -> clientSecret.stringValue,
                "code"          -> code,
                "redirect_uri"  -> REDIRECT_URI,
              ),
            ),
          )
          .addHeader(Header.ContentType(MediaType.application.json))
          .addHeader(Header.Accept(MediaType.application.json)),
      )
      _             <- ZIO
        .fail(new Exception(s"GitHub token exchange failed: ${response.status}"))
        .when(!response.status.isSuccess)
      tokenResponse <- response.body
        .to[GitHubToken]
        .mapError(error => new Exception(s"Failed to parse GitHub token response: $error"))
    } yield tokenResponse
  }

  private def fetchGitHubUser(accessToken: String): ZIO[Client, Throwable, GitHubUser] = {
    for {
      response <- ZClient.batched(
        Request
          .get(GITHUB_USER_API)
          .addHeader(Header.Authorization.Bearer(accessToken))
          .addHeader(Header.Accept(MediaType.application.json)),
      )
      _        <- ZIO
        .fail(new Exception(s"GitHub user API failed: ${response.status}"))
        .when(!response.status.isSuccess)
      user     <- response.body
        .to[GitHubUser]
        .mapError(error => new Exception(s"Failed to parse GitHub user response: $error"))
    } yield user
  }

}

object GithubAuthService {
  def make(ghClientID: String, ghClientSecret: Secret): UIO[GithubAuthService] =
    for {
      redirects <- Ref.make(Map.empty[String, URI])
      users     <- Ref.make(Map.empty[String, GitHubUser])
    } yield new GithubAuthService(redirects, users, ghClientID, ghClientSecret)

  def live(ghClientID: String, ghClientSecret: Secret): ZLayer[Any, Nothing, GithubAuthService] =
    ZLayer.fromZIO(make(ghClientID, ghClientSecret))
}
