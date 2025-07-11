package example.auth.bearer.jwt.refresh

import zio._
import zio.http._
import zio.json._

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
  val greetUrl   = URL.decode(s"$url/profile/me").toOption.get

  // Token response case class
  case class TokenResponse(accessToken: String, refreshToken: String, tokenType: String, expiresIn: Int)

  // Refresh token request case class
  case class RefreshTokenRequest(refreshToken: String)

  // JSON codecs
  implicit val tokenResponseDecoder: JsonDecoder[TokenResponse]             = DeriveJsonDecoder.gen[TokenResponse]
  implicit val refreshTokenRequestEncoder: JsonEncoder[RefreshTokenRequest] = DeriveJsonEncoder.gen[RefreshTokenRequest]

  // Token storage
  case class TokenStore(accessToken: String, refreshToken: String)

  def login(client: Client, username: String, password: String): IO[Response, TokenResponse] = {
    val loginData = Map("username" -> username, "password" -> password)

    client
      .batched(
        Request
          .post(loginUrl, Body.fromString(loginData.toJson))
          .addHeader(Header.ContentType(MediaType.application.json)),
      )
      .orDie
      .flatMap { response =>
        if (response.status.isSuccess)
          response.body.asString
            .mapError(_ => Response.badRequest("Failed to read response body"))
            .flatMap { body =>
              ZIO.fromEither(body.fromJson[TokenResponse]).mapError(_ => Response.badRequest("Invalid JSON response"))
            }
        else
          ZIO.fail(response)
      }
  }

  def refreshToken(client: Client, refreshToken: String): IO[Response, TokenResponse] = {
    val refreshRequest = RefreshTokenRequest(refreshToken)

    client
      .batched(
        Request
          .post(refreshUrl, Body.fromString(refreshRequest.toJson))
          .addHeader(Header.ContentType(MediaType.application.json)),
      )
      .orDie
      .flatMap { response =>
        if (response.status.isSuccess)
          response.body.asString
            .mapError(_ => Response.badRequest("Failed to read response body"))
            .flatMap { body =>
              ZIO.fromEither(body.fromJson[TokenResponse]).mapError(_ => Response.badRequest("Invalid JSON response"))
            }
        else
          ZIO.fail(response)
      }
  }

  def makeAuthenticatedRequest(
    client: Client,
    request: Request,
    tokenStore: Ref[Option[TokenStore]],
  ): IO[Response, Response] = {
    def attemptRequest(accessToken: String): IO[Response, Response] = {
      client.batched(request.addHeader(Header.Authorization.Bearer(accessToken)))
    }.orDie

    def refreshAndRetry(currentTokenStore: TokenStore): IO[Response, Response] = {
      refreshToken(client, currentTokenStore.refreshToken).flatMap { newTokens =>
        val newStore = TokenStore(newTokens.accessToken, newTokens.refreshToken)
        tokenStore.set(Some(newStore)) *>
          attemptRequest(newTokens.accessToken)
      }.catchAll { refreshError =>
        // Refresh failed, clear token store and fail
        tokenStore.set(None) *> ZIO.fail(refreshError)
      }
    }

    tokenStore.get.flatMap {
      case Some(tokens) =>
        attemptRequest(tokens.accessToken).catchAll { response =>
          if (response.status == Status.Unauthorized)
            // Access token might be expired, try to refresh
            Console.printLine("Access token expired, attempting to refresh...").orDie *>
              refreshAndRetry(tokens)
          else
            ZIO.fail(response)
        }
      case None         =>
        ZIO.fail(Response.unauthorized("No authentication tokens available"))
    }
  }

  val program = for {
    client     <- ZIO.service[Client]
    tokenStore <- Ref.make(Option.empty[TokenStore])

    // Step 1: Login to get tokens
    _             <- Console.printLine("Logging in...")
    tokenResponse <- login(client, "John", "nhoJ")
    _             <- Console.printLine(s"Login successful! Access token expires in ${tokenResponse.expiresIn} seconds")

    // Store the tokens
    initialTokenStore = TokenStore(tokenResponse.accessToken, tokenResponse.refreshToken)
    _ <- tokenStore.set(Some(initialTokenStore))

    // Step 2: Make authenticated request
    _        <- Console.printLine("Making authenticated request...")
    response <- makeAuthenticatedRequest(client, Request.get(greetUrl), tokenStore)
    body     <- response.body.asString
    _        <- Console.printLine(s"Response: $body")

    // Step 3: Simulate token expiration by waiting and making another request
    _ <- Console.printLine("Waiting 310 seconds to simulate token expiration...")
    _ <- ZIO.sleep(310.seconds)

    _         <- Console.printLine("Making another authenticated request (should trigger refresh)...")
    response2 <- makeAuthenticatedRequest(client, Request.get(greetUrl), tokenStore)
    body2     <- response2.body.asString
    _         <- Console.printLine(s"Response after refresh: $body2")

  } yield ()

  // Alternative program without waiting for expiration (for quick testing)
  val quickProgram = for {
    client     <- ZIO.service[Client]
    tokenStore <- Ref.make(Option.empty[TokenStore])

    // Login
    _             <- Console.printLine("Logging in...")
    tokenResponse <- login(client, "John", "nhoJ")
    _             <- Console.printLine(s"Login successful!")

    // Store tokens
    initialTokenStore = TokenStore(tokenResponse.accessToken, tokenResponse.refreshToken)
    _ <- tokenStore.set(Some(initialTokenStore))

    // Make authenticated request
    _        <- Console.printLine("Making authenticated request...")
    response <- makeAuthenticatedRequest(client, Request.get(greetUrl), tokenStore)
    body     <- response.body.asString
    _        <- Console.printLine(s"Response: $body")

    // Manually test refresh
    _         <- Console.printLine("Testing token refresh...")
    newTokens <- refreshToken(client, tokenResponse.refreshToken)
    _         <- Console.printLine(s"Refresh successful! New access token obtained")

    // Test with new tokens
    newTokenStore = TokenStore(newTokens.accessToken, newTokens.refreshToken)
    _         <- tokenStore.set(Some(newTokenStore))
    response2 <- makeAuthenticatedRequest(client, Request.get(greetUrl), tokenStore)
    body2     <- response2.body.asString
    _         <- Console.printLine(s"Response with refreshed token: $body2")

  } yield ()

  override val run = quickProgram.provide(Client.default)
}
