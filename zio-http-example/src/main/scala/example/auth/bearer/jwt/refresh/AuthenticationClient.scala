package example.auth.bearer.jwt.refresh

import zio._
import zio.json._

import zio.http._

import example.auth.bearer.jwt.refresh.core.TokenResponse

object AuthenticationClient extends ZIOAppDefault {

  /**
   * This example demonstrates accessing a protected route with refresh token
   * support. It first makes a login request to obtain both access and refresh
   * tokens, then uses the access token to access protected routes. If the
   * access token expires, it automatically refreshes using the refresh token.
   */
  val url = "http://localhost:8080"

  val loginUrl   = URL.decode(s"$url/login").toOption.get
  val refreshUrl = URL.decode(s"$url/refresh").toOption.get
  val profileUrl = URL.decode(s"$url/profile/me").toOption.get
  val adminUrl   = URL.decode(s"$url/admin/users").toOption.get
  val logoutUrl  = URL.decode(s"$url/logout").toOption.get

  // Token storage
  case class TokenStore(accessToken: String, refreshToken: String)

  // Service for managing authentication
  trait AuthenticationService {
    def login(username: String, password: String): IO[Throwable, TokenResponse]
    def refreshTokens(refreshToken: String): IO[Throwable, TokenResponse]
    def makeAuthenticatedRequest(request: Request): IO[Throwable, Response]
    def logout(refreshToken: String): IO[Throwable, Unit]
  }

  case class AuthenticationServiceLive(
    client: Client,
    tokenStore: Ref[Option[TokenStore]],
  ) extends AuthenticationService {

    def login(username: String, password: String): IO[Throwable, TokenResponse] = {
      val formData = Form(
        FormField.simpleField("username", username),
        FormField.simpleField("password", password),
      )

      for {
        response <- client
          .batched(Request.post(loginUrl, Body.fromURLEncodedForm(formData)))
          .orDie
        _        <- ZIO.when(!response.status.isSuccess) {
          ZIO.fail(new Exception(s"Login failed: ${response.status}"))
        }
        body     <- response.body.asString
        tokens   <- ZIO
          .fromEither(body.fromJson[TokenResponse])
          .mapError(e => new Exception(s"Failed to parse token response: $e"))
        _        <- tokenStore.set(Some(TokenStore(tokens.accessToken, tokens.refreshToken)))
      } yield tokens
    }

    def refreshTokens(refreshToken: String): IO[Throwable, TokenResponse] = {
      val formData = Form(
        FormField.simpleField("refreshToken", refreshToken),
      )

      for {
        response <- client
          .batched(Request.post(refreshUrl, Body.fromURLEncodedForm(formData)))
          .orDie
        _        <- ZIO.when(!response.status.isSuccess) {
          ZIO.fail(new Exception(s"Token refresh failed: ${response.status}"))
        }
        body     <- response.body.asString
        tokens   <- ZIO
          .fromEither(body.fromJson[TokenResponse])
          .mapError(e => new Exception(s"Failed to parse token response: $e"))
        _        <- tokenStore.set(Some(TokenStore(tokens.accessToken, tokens.refreshToken)))
      } yield tokens
    }

    def makeAuthenticatedRequest(request: Request): IO[Throwable, Response] = {
      def attemptRequest(accessToken: String): IO[Throwable, Response] =
        client.batched(request.addHeader(Header.Authorization.Bearer(accessToken))).orDie

      def refreshAndRetry(currentTokenStore: TokenStore): IO[Throwable, Response] =
        for {
          _         <- Console.printLine("Access token expired, refreshing...").orDie
          newTokens <- refreshTokens(currentTokenStore.refreshToken)
          response  <- attemptRequest(newTokens.accessToken)
        } yield response

      tokenStore.get.flatMap {
        case Some(tokens) =>
          attemptRequest(tokens.accessToken).flatMap { response =>
            if (response.status == Status.Unauthorized) {
              refreshAndRetry(tokens)
            } else {
              ZIO.succeed(response)
            }
          }
        case None         =>
          ZIO.fail(new Exception("No authentication tokens available"))
      }
    }

    def logout(refreshToken: String): IO[Throwable, Unit] = {
      val formData = Form(FormField.simpleField("refreshToken", refreshToken))

      for {
        response <- client
          .batched(Request.post(logoutUrl, Body.fromURLEncodedForm(formData)))
          .orDie
        _        <- ZIO.when(!response.status.isSuccess) {
          ZIO.fail(new Exception(s"Logout failed: ${response.status}"))
        }
        _        <- tokenStore.set(None)
      } yield ()
    }
  }

  val program = for {
    client     <- ZIO.service[Client]
    tokenStore <- Ref.make(Option.empty[TokenStore])
    authService = AuthenticationServiceLive(client, tokenStore)

    // Step 1: Login
    _      <- Console.printLine("=== Login ===")
    tokens <- authService.login("john", "password123")
    _      <- Console.printLine(s"Login successful! Access token expires in ${tokens.expiresIn} seconds")

    // Step 2: Access protected profile
    _               <- Console.printLine("\n=== Accessing Profile ===")
    profileResponse <- authService.makeAuthenticatedRequest(Request.get(profileUrl))
    profileBody     <- profileResponse.body.asString
    _               <- Console.printLine(s"Profile: $profileBody")

    // Step 3: Try admin access with non-admin user
    _             <- Console.printLine("\n=== Trying Admin Access (as non-admin) ===")
    adminResponse <- authService.makeAuthenticatedRequest(Request.get(adminUrl))
    _             <-
      if (adminResponse.status == Status.Forbidden) {
        Console.printLine("Access denied (expected for non-admin user)")
      } else {
        adminResponse.body.asString.flatMap(Console.printLine(_))
      }

    // Step 4: Login as admin
    _           <- Console.printLine("\n=== Login as Admin ===")
    adminTokens <- authService.login("admin", "admin123")
    _           <- Console.printLine("Admin login successful!")

    // Step 5: Access admin area
    _              <- Console.printLine("\n=== Accessing Admin Area ===")
    adminResponse2 <- authService.makeAuthenticatedRequest(Request.get(adminUrl))
    adminBody      <- adminResponse2.body.asString
    _              <- Console.printLine(s"Admin area: $adminBody")

    // Step 6: Test token refresh (simulate expiration)
    _            <- Console.printLine("\n=== Testing Token Refresh ===")
    _            <- Console.printLine("Simulating token expiration by manually refreshing...")
    currentStore <- tokenStore.get
    refreshToken = currentStore.map(_.refreshToken).getOrElse("")
    newTokens <- authService.refreshTokens(refreshToken)
    _         <- Console.printLine(s"Tokens refreshed successfully! New access token obtained")

    // Step 7: Access profile with new tokens
    profileResponse2 <- authService.makeAuthenticatedRequest(Request.get(profileUrl))
    profileBody2     <- profileResponse2.body.asString
    _                <- Console.printLine(s"Profile with refreshed token: $profileBody2")

    // Step 8: Logout
    _          <- Console.printLine("\n=== Logout ===")
    finalStore <- tokenStore.get
    finalRefreshToken = finalStore.map(_.refreshToken).getOrElse("")
    _ <- authService.logout(finalRefreshToken)
    _ <- Console.printLine("Logged out successfully")

    // Step 9: Try to access protected route after logout (should fail)
    _ <- Console.printLine("\n=== Trying to access after logout ===")
    _ <- authService
      .makeAuthenticatedRequest(Request.get(profileUrl))
      .catchAll(error => Console.printLine(s"Access denied after logout: ${error.getMessage}"))

  } yield ()

  override val run = program.provide(Client.default)
}
