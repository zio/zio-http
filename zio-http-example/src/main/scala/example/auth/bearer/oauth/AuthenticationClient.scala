package example.auth.bearer.oauth

import example.auth.bearer.oauth.core._
import zio._
import zio.http._
import zio.http.template.Html
import zio.json._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

import java.awt.Desktop
import java.net.URI

object AuthenticationClient extends ZIOAppDefault {

  /**
   * OAuth Authentication Client using GitHub. This client implements the OAuth
   * authorization code flow.
   *
   * Flow:
   *   1. Start a local callback server
   *   2. Open browser to initiate OAuth flow
   *   3. Wait for callback with tokens
   *   4. Use tokens for authenticated requests
   *   5. Handle token refresh automatically
   */
  private val serverUrl    = "http://localhost:8080"
  private val callbackPort = 3000
  private val callbackUrl  = s"http://localhost:$callbackPort"

  private val refreshUrl = URL.decode(s"$serverUrl/refresh").toOption.get
  private val profileUrl = URL.decode(s"$serverUrl/profile/me").toOption.get

  case class TokenStore(accessToken: String, refreshToken: String)

  def startCallbackServer(tokenPromise: Promise[Throwable, TokenResponse]): ZIO[Any, Throwable, Server] = {
    val callbackRoutes = Routes(
      Method.GET / Root -> handler { (request: Request) =>
        val queryParams  = request.url.queryParams
        val accessToken  = queryParams.queryParams("access_token").headOption
        val refreshToken = queryParams.queryParams("refresh_token").headOption
        val tokenType    = queryParams.queryParams("token_type").headOption
        val expiresIn    = queryParams.queryParams("expires_in").headOption.flatMap(_.toLongOption)

        (accessToken, refreshToken, tokenType, expiresIn) match {
          case (Some(at), Some(rt), Some(tt), Some(exp)) =>
            val tokens = TokenResponse(at, rt, tt, exp)
            tokenPromise
              .succeed(tokens)
              .as(
                Response.html(
                  Html.raw(
                    """
                      |<!DOCTYPE html>
                      |<html>
                      |<head><title>Authentication Successful</title></head>
                      |<body>
                      |  <h1>Authentication Successful!</h1>
                      |  <p>You have successfully authenticated with GitHub.</p>
                      |  <p>You can now close this window and return to the application.</p>
                      |  <script>setTimeout(() => window.close(), 5000);</script>
                      |</body>
                      |</html>
                """.stripMargin,
                  ),
                ),
              )

          case _ =>
            val error            = queryParams.queryParams("error").headOption.getOrElse("Unknown error")
            val errorDescription = queryParams.queryParams("error_description").headOption.getOrElse("")

            tokenPromise
              .fail(new RuntimeException(s"OAuth error: $error - $errorDescription"))
              .as(
                Response.html(
                  Html.raw(
                    s"""
                       |<!DOCTYPE html>
                       |<html>
                       |<head><title>Authentication Failed</title></head>
                       |<body>
                       |  <h1>Authentication Failed</h1>
                       |  <p>Error: $error</p>
                       |  <p>$errorDescription</p>
                       |  <p>Please try again.</p>
                       |</body>
                       |</html>
                """.stripMargin,
                  ),
                ),
              )
        }
      },
    )

    Server.serve(callbackRoutes).provide(Server.defaultWithPort(callbackPort))
  }

  def openBrowser(url: String): ZIO[Any, Throwable, Unit] = {
    val uri = new URI(url)

    val desktopAttempt = ZIO.attempt {
      val desktop = Desktop.getDesktop
      if (Desktop.isDesktopSupported && desktop.isSupported(Desktop.Action.BROWSE))
        desktop.browse(uri)
      else
        throw new UnsupportedOperationException("Desktop browse not supported")
    }

    val fallbackAttempt = ZIO.attempt {
      val os      = java.lang.System.getProperty("os.name").toLowerCase
      val command =
        if (os.contains("mac")) Seq("open", url)
        else if (os.contains("linux")) Seq("xdg-open", url)
        else throw new UnsupportedOperationException("No known fallback command for this OS")

      val exitCode = new ProcessBuilder(command: _*).start().waitFor()
      if (exitCode != 0)
        throw new RuntimeException(s"Fallback browser command failed with exit code $exitCode")
    }

    desktopAttempt.orElse(fallbackAttempt).catchAll { _: Throwable =>
      Console.printLine(s"Unable to open browser automatically. Please open the following URL: $url")
    }
  }

  def performOAuthFlow(): ZIO[Any, Throwable, TokenResponse] = for {
    tokenPromise <- Promise.make[Throwable, TokenResponse]

    // Start callback server
    serverFiber <- startCallbackServer(tokenPromise).fork

    // Build OAuth URL
    oauthUrl = s"$serverUrl/auth/github?redirect_uri=$callbackUrl"

    // Open browser for OAuth flow
    _ <- Console.printLine("Starting OAuth flow...")
    _ <- Console.printLine(s"Opening browser to: $oauthUrl")
    _ <- openBrowser(oauthUrl)

    // Wait for callback
    _      <- Console.printLine("Waiting for OAuth callback...")
    tokens <- tokenPromise.await.timeoutFail(new RuntimeException("OAuth flow timed out"))(5.minutes)

    // Stop callback server
    _ <- serverFiber.interrupt

  } yield tokens

  def refreshToken(token: String): ZIO[Client, Throwable, TokenResponse] =
    for {
      response      <- ZClient.batched(
        Request
          .post(refreshUrl, Body.from(Map("refreshToken" -> token)))
          .addHeader(Header.ContentType(MediaType.application.json)),
      )
      _             <- ZIO
        .fail(new RuntimeException(s"Refresh failed: ${response.status}"))
        .when(!response.status.isSuccess)
      body          <- response.body.asString
      tokenResponse <- ZIO
        .fromEither(body.fromJson[TokenResponse])
        .mapError(error => new RuntimeException(s"Failed to parse refresh response: $error"))
    } yield tokenResponse

  def makeAuthenticatedRequest(
    request: Request,
    tokenStore: Ref[Option[TokenStore]],
  ): ZIO[Client, Throwable, Response] = {
    def attemptRequest(accessToken: String): ZIO[Client, Throwable, Response] =
      ZClient.batched(request.addHeader(Header.Authorization.Bearer(accessToken)))

    def refreshAndRetry(currentTokenStore: TokenStore): ZIO[Client, Throwable, Response] =
      for {
        newTokens <- refreshToken(currentTokenStore.refreshToken)
        newStore = TokenStore(newTokens.accessToken, newTokens.refreshToken)
        _        <- tokenStore.set(Some(newStore))
        response <- attemptRequest(newTokens.accessToken)
      } yield response

    for {
      tokenStoreValue <- tokenStore.get
      response        <- tokenStoreValue match {
        case Some(tokens) =>
          attemptRequest(tokens.accessToken).catchAll { error =>
            // Try to refresh if it looks like an auth error
            if (error.getMessage.contains("401") || error.getMessage.contains("Unauthorized"))
              refreshAndRetry(tokens)
            else ZIO.fail(error)
          }
        case None         =>
          ZIO.fail(new RuntimeException("No authentication tokens available"))
      }
    } yield response
  }

  val program: ZIO[Client, Throwable, Unit] =
    for {
      // Step 1: Perform OAuth flow
      _      <- Console.printLine("=== GitHub OAuth Authentication ===")
      tokens <- performOAuthFlow()
      _      <- Console.printLine("OAuth flow completed successfully!")
      _      <- Console.printLine(s"Access token expires in: ${tokens.expiresIn} seconds")

      // Step 2: Store tokens
      tokenStore <- Ref.make(Option(TokenStore(tokens.accessToken, tokens.refreshToken)))

      // Step 3: Test authenticated request
      _        <- Console.printLine("\n=== Testing Authenticated Request ===")
      response <- makeAuthenticatedRequest(Request.get(profileUrl), tokenStore)

      _ <- response.status match {
        case Status.Ok =>
          for {
            body <- response.body.asString
            _    <- Console.printLine("User Profile:")
            user <- ZIO
              .fromEither(body.fromJson[GitHubUser])
              .catchAll(_ => ZIO.succeed(body))
            _    <- user match {
              case gitHubUser: GitHubUser =>
                Console.printLine(s"  ID: ${gitHubUser.id}") *>
                  Console.printLine(s"  Username: ${gitHubUser.login}") *>
                  Console.printLine(s"  Name: ${gitHubUser.name.getOrElse("N/A")}") *>
                  Console.printLine(s"  Email: ${gitHubUser.email.getOrElse("N/A")}") *>
                  Console.printLine(s"  Avatar: ${gitHubUser.avatar_url}")
              case other                  =>
                Console.printLine(s"  Raw response: $other")
            }
          } yield ()
        case _         =>
          for {
            body <- response.body.asString
            _    <- Console.printLine(s"Error: ${response.status}")
            _    <- Console.printLine(s"Response: $body")
          } yield ()
      }

      // Step 4: Test token refresh
      _         <- Console.printLine("\n=== Testing Token Refresh ===")
      newTokens <- refreshToken(tokens.refreshToken)
      _         <- Console.printLine("Token refresh successful!")

      // Step 5: Test with refreshed tokens
      _         <- tokenStore.set(Some(TokenStore(newTokens.accessToken, newTokens.refreshToken)))
      response2 <- makeAuthenticatedRequest(Request.get(profileUrl), tokenStore)

      _ <- response2.status match {
        case Status.Ok =>
          Console.printLine("Authenticated request with refreshed token successful!")
        case _         =>
          for {
            body <- response2.body.asString
            _    <- Console.printLine(s"Error with refreshed token: ${response2.status}")
            _    <- Console.printLine(s"Response: $body")
          } yield ()
      }

      _ <- Console.printLine("\n=== Authentication Demo Complete ===")

    } yield ()

  override val run = program.provide(Client.default)
}
