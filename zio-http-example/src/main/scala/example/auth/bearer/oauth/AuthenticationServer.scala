package example.auth.bearer.oauth

import example.auth.bearer.oauth.models._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio._
import zio.http._
import zio.json._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Clock
import scala.io.Source
import scala.util.Try

/**
 * OAuth 2.0 Authentication Server using GitHub as the identity provider. This
 * server implements the OAuth 2.0 authorization code flow with GitHub.
 *
 * Setup:
 *   1. Create a GitHub OAuth App at https://github.com/settings/developers
 *   2. Set Authorization callback URL to:
 *      http://localhost:8080/auth/github/callback
 *   3. Replace CLIENT_ID and CLIENT_SECRET with your GitHub app credentials
 */
object AuthenticationServer extends ZIOAppDefault {
  implicit val clock: Clock = Clock.systemUTC

  override val run = {
    for {
      authService <- AuthService.make
      routes      <- authService.routes
    } yield routes
  }.flatMap(routes => Server.serve(routes).provide(Server.default, Client.default))
}

/**
 * Consolidated application state containing all authentication-related data
 */
case class AuthState(
  refreshTokens: Map[String, String], // refreshToken -> userId
  redirectUris: Map[String, URI],     // state -> redirectUri
  users: Map[String, GitHubUser],     // userId -> GitHubUser
)

object AuthState {
  val empty: AuthState = AuthState(
    refreshTokens = Map.empty,
    redirectUris = Map.empty,
    users = Map.empty,
  )
}

/**
 * Authentication Service that handles OAuth flow and JWT token management
 */
class AuthService private (
  private val authState: Ref[AuthState],
) {

  // GitHub OAuth Configuration
  private val REDIRECT_URI         = "http://localhost:8080/auth/github/callback"
  private val GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
  private val GITHUB_TOKEN_URL     = "https://github.com/login/oauth/access_token"
  private val GITHUB_USER_API      = "https://api.github.com/user"

  private val GH_CLIENT_ID     = "<github-client-id>"
  private val GH_CLIENT_SECRET = "<github-client-secret>"

  private val JWT_SECRET_KEY = "secretKey"
  private val EXPIRES_IN     = 3600L

  implicit val clock: Clock = Clock.systemUTC

  def jwtEncode(userId: String, key: String, expirationSeconds: Long): String =
    Jwt.encode(JwtClaim(subject = Some(userId)).issuedNow.expiresIn(expirationSeconds), key, JwtAlgorithm.HS512)

  def jwtDecode(token: String, key: String): Try[JwtClaim] =
    Jwt.decode(token, key, Seq(JwtAlgorithm.HS512))

  def generateRandomString(): ZIO[Any, Nothing, String] =
    ZIO.randomWith(_.nextUUID).map(_.toString.replace("-", ""))

  def exchangeCodeForToken(code: String): ZIO[Client, Throwable, GitHubTokenResponse] = {
    for {
      response      <- ZClient.batched(
        Request
          .post(
            GITHUB_TOKEN_URL,
            Body.from(
              Map(
                "client_id"     -> GH_CLIENT_ID,
                "client_secret" -> GH_CLIENT_SECRET,
                "code"          -> code,
                "redirect_uri"  -> REDIRECT_URI,
              ),
            ),
          )
          .addHeader(Header.ContentType(MediaType.application.json))
          .addHeader(Header.Accept(MediaType.application.json)),
      )
      _             <- ZIO
        .fail(new RuntimeException(s"GitHub token exchange failed: ${response.status}"))
        .when(!response.status.isSuccess)
      body          <- response.body.asString
      tokenResponse <- ZIO
        .fromEither(body.fromJson[GitHubTokenResponse])
        .mapError(error => new RuntimeException(s"Failed to parse GitHub token response: $error"))
    } yield tokenResponse
  }

  def fetchGitHubUser(accessToken: String): ZIO[Client, Throwable, GitHubUser] = {
    for {
      response <- ZClient.batched(
        Request
          .get(GITHUB_USER_API)
          .addHeader(Header.Authorization.Bearer(accessToken))
          .addHeader(Header.Accept(MediaType.application.json)),
      )
      _        <- ZIO
        .fail(new RuntimeException(s"GitHub user API failed: ${response.status}"))
        .when(!response.status.isSuccess)
      body     <- response.body.asString
      user     <- ZIO
        .fromEither(body.fromJson[GitHubUser])
        .mapError(error => new RuntimeException(s"Failed to parse GitHub user response: $error"))
    } yield user
  }

  val bearerAuthWithContext: HandlerAspect[Any, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) =>
          ZIO
            .fromTry(jwtDecode(token.value.asString, JWT_SECRET_KEY))
            .orElseFail(Response.unauthorized("Invalid or expired token!"))
            .flatMap(claim => ZIO.fromOption(claim.subject).orElseFail(Response.badRequest("Missing subject claim!")))
            .map(u => (request, u))

        case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))))
      }
    })

  def routes: UIO[Routes[Client, Response]] = ZIO.succeed {
    Routes(

      Method.GET / Root -> handler { (_: Request) =>
        for {
          html <- loadHtmlFromResources("/oauth-client.html").orDie
        } yield Response(
          status = Status.Ok,
          headers = Headers(Header.ContentType(MediaType.text.html)),
          body = Body.fromString(html),
        )
      },

      // Protected route - requires valid JWT token
      Method.GET / "profile" / "me" -> handler { (request: Request) =>
        ZIO.service[String].flatMap { userId =>
          authState.get.map { state =>
            state.users.get(userId) match {
              case Some(user) =>
                Response.json(user.toJson)
              case None       =>
                Response(
                  status = Status.NotFound,
                  body = Body.from(
                    Map("userId" -> userId, "message" -> "User profile not found"),
                  ),
                )
            }
          }
        }
      } @@ bearerAuthWithContext,

      // Start OAuth flow - redirect to GitHub
      Method.GET / "auth" / "github" -> handler { (request: Request) =>
        for {
          state         <- generateRandomString()
          redirectUri   <- ZIO.succeed(
            URI.create(
              request.url.queryParams.queryParams("redirect_uri").headOption.getOrElse("http://localhost:3000"),
            ),
          )
          _             <- authState.update(currentState =>
            currentState.copy(redirectUris = currentState.redirectUris.updated(state, redirectUri)),
          )
          githubAuthUrl <- ZIO
            .fromEither(
              URL.decode(
                s"$GITHUB_AUTHORIZE_URL?client_id=$GH_CLIENT_ID&redirect_uri=$REDIRECT_URI&scope=user:email&state=$state",
              ),
            )
            .orDie
        } yield Response.status(Status.Found).addHeader(Header.Location(githubAuthUrl))
      },

      // OAuth callback - handle GitHub callback
      Method.GET / "auth" / "github" / "callback" -> handler { (request: Request) =>
        val queryParams = request.url.queryParams
        // Authorization code returned by GitHub after successful user authentication
        // This temporary code is exchanged for an access token
        val code        = queryParams.queryParams("code").headOption
        //  The same random string your server generated and sent to GitHub initially
        // prevents malicious sites from initiating fake OAuth flows
        // Your server must verify this matches the state it originally sent
        val state       = queryParams.queryParams("state").headOption
        // Error code indicating why the OAuth flow failed
        val error       = queryParams.queryParams("error").headOption

        error match {
          case Some(err) =>
            ZIO.succeed(Response.badRequest(s"OAuth error: $err"))
          case None      =>
            (code, state) match {
              case (Some(authCode), Some(stateParam)) =>
                for {
                  currentState <- authState.get
                  oauthState   <- ZIO
                    .fromOption(currentState.redirectUris.get(stateParam))
                    .orElseFail(Response.badRequest("Invalid state parameter"))

                  // Exchange code for access token
                  githubTokens <- exchangeCodeForToken(authCode)
                    .mapError(err => Response.badRequest(s"Token exchange failed: ${err.getMessage}"))

                  // Fetch user info from GitHub
                  githubUser <- fetchGitHubUser(githubTokens.access_token)
                    .mapError(err => Response.badRequest(s"Failed to fetch user info: ${err.getMessage}"))

                  // Generate our own JWT tokens
                  accessToken = jwtEncode(githubUser.id.toString, JWT_SECRET_KEY, EXPIRES_IN)
                  refreshToken <- generateRandomString()

                  // Clean up state, store user info, and store refresh token
                  _ <- authState.update(state =>
                    state.copy(
                      redirectUris = state.redirectUris.removed(stateParam),
                      users = state.users.updated(githubUser.id.toString, githubUser),
                      refreshTokens = state.refreshTokens.updated(refreshToken, githubUser.id.toString),
                    ),
                  )

                  tokenResponse = TokenResponse(accessToken, "Bearer", EXPIRES_IN, refreshToken)

                  // Redirect back to the client with tokens
                  redirectUrl <-
                    ZIO
                      .fromEither(
                        URL.decode(
                          s"${oauthState}?access_token=${tokenResponse.accessToken}&refresh_token=${tokenResponse.refreshToken}&token_type=${tokenResponse.tokenType}&expires_in=${tokenResponse.expiresIn}",
                        ),
                      )
                      .orDie

                } yield Response.status(Status.Found).addHeader(Header.Location(redirectUrl))

              case _ =>
                ZIO.succeed(Response.badRequest("Missing code or state parameter"))
            }
        }
      },

      // Refresh token endpoint
      Method.POST / "refresh" -> handler { (request: Request) =>
        for {
          body           <- request.body.asString.mapError(_ => Response.badRequest("Failed to read request body"))
          refreshRequest <- ZIO
            .fromEither(body.fromJson[Map[String, String]])
            .mapError(_ => Response.badRequest("Invalid JSON"))
          refreshToken   <- ZIO
            .fromOption(refreshRequest.get("refreshToken"))
            .orElseFail(Response.badRequest("Missing refresh token"))

          currentState <- authState.get
          userId       <- ZIO
            .fromOption(currentState.refreshTokens.get(refreshToken))
            .orElseFail(Response.unauthorized("Invalid refresh token"))

          newAccessToken = jwtEncode(userId, JWT_SECRET_KEY, EXPIRES_IN)
          newRefreshToken <- generateRandomString()

          _ <- authState.update(state =>
            state.copy(
              refreshTokens = state.refreshTokens.removed(refreshToken).updated(newRefreshToken, userId),
            ),
          )

          tokenResponse = TokenResponse(
            accessToken = newAccessToken,
            tokenType = "Bearer",
            expiresIn = EXPIRES_IN,
            refreshToken = newRefreshToken,
          )
        } yield Response.json(tokenResponse.toJson)
      },
    ) @@ Middleware.debug
  }


  /**
   * Loads HTML content from the resources directory
   */
  def loadHtmlFromResources(resourcePath: String): ZIO[Any, Throwable, String] = {
    ZIO.attempt {
      val inputStream = getClass.getResourceAsStream(resourcePath)
      if (inputStream == null) throw new RuntimeException(s"Resource not found: $resourcePath")

      val source = Source.fromInputStream(inputStream, StandardCharsets.UTF_8.name())
      try source.mkString
      finally {
        source.close()
        inputStream.close()
      }
    }
  }

}

object AuthService {
  def make: UIO[AuthService] =
    Ref.make(AuthState.empty).map(new AuthService(_))
}

// Data structures
case class GitHubTokenResponse(
  access_token: String,
  token_type: String,
  scope: String,
)
object GitHubTokenResponse {
  implicit val decoder: JsonDecoder[GitHubTokenResponse] =
    DeriveJsonDecoder.gen
}

