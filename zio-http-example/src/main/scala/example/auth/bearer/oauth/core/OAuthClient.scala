package example.auth.bearer.oauth.core

import java.awt.Desktop
import java.net.URI

import zio._
import zio.json._

import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

import zio.http._
import zio.http.template.Html

/**
 * OAuth Authentication Client using GitHub. This client implements the OAuth
 * authorization code flow.
 */
trait OAuthClient {
  def makeAuthenticatedRequest(request: Request): IO[Throwable, Response]
  def login: IO[Throwable, Unit]
  def logout: IO[Throwable, Unit]
}

object OAuthClient {
  def live = ZLayer {
    for {
      client     <- ZIO.service[Client]
      tokenStore <- Ref.make(Option.empty[Token])
    } yield GithubOAuthClient(client, tokenStore)
  }
}

case class GithubOAuthClient(
  client: Client,
  tokenStore: Ref[Option[Token]],
) extends OAuthClient {

  private val serverUrl    = "http://localhost:8080"
  private val callbackPort = 3000
  private val callbackUrl  = s"http://localhost:$callbackPort"

  private val refreshUrl = URL.decode(s"$serverUrl/refresh").toOption.get
  private val logoutUrl  = URL.decode(s"$serverUrl/logout").toOption.get

  private def startCallbackServer(tokenPromise: Promise[Throwable, Token]): ZIO[Any, Throwable, Server] = {
    val callbackRoutes = Routes(
      Method.GET / Root -> handler { (request: Request) =>
        val queryParams  = request.url.queryParams
        val accessToken  = queryParams.queryParams("access_token").headOption
        val refreshToken = queryParams.queryParams("refresh_token").headOption
        val tokenType    = queryParams.queryParams("token_type").headOption
        val expiresIn    = queryParams.queryParams("expires_in").headOption.flatMap(_.toLongOption)

        (accessToken, refreshToken, tokenType, expiresIn) match {
          case (Some(at), Some(rt), Some(tt), Some(exp)) =>
            val tokens = Token(at, rt, tt, exp)
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
              .fail(new Exception(s"OAuth error: $error - $errorDescription"))
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

  private def openBrowser(url: String): ZIO[Any, Throwable, Unit] = {
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

  override def login: IO[Throwable, Unit] =
    for {
      tokenPromise <- Promise.make[Throwable, Token]

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
      tokens <- tokenPromise.await.timeoutFail(new Exception("OAuth flow timed out"))(5.minutes)
      _      <- tokenStore.set(Some(tokens))

      // Stop callback server
      _ <- serverFiber.interrupt

    } yield ()

  private def refreshTokens(refreshToken: String): IO[Throwable, Token] =
    for {
      response      <- client.batched(
        Request
          .post(refreshUrl, Body.from(Map("refreshToken" -> refreshToken)))
          .addHeader(Header.ContentType(MediaType.application.json)),
      )
      _             <- ZIO
        .fail(new RuntimeException(s"Refresh failed: ${response.status}"))
        .when(!response.status.isSuccess)
      body          <- response.body.asString
      tokenResponse <- ZIO
        .fromEither(body.fromJson[Token])
        .mapError(error => new RuntimeException(s"Failed to parse refresh response: $error"))
    } yield tokenResponse

  override def makeAuthenticatedRequest(request: Request): IO[Throwable, Response] = {
    def attemptRequest(accessToken: String): ZIO[Any, Throwable, Response] =
      client.batched(request.addHeader(Header.Authorization.Bearer(accessToken)))

    def refreshAndRetry(currentTokenStore: Token): ZIO[Any, Throwable, Response] =
      for {
        newTokens <- refreshTokens(currentTokenStore.refreshToken)
        _         <- tokenStore.set(Some(newTokens))
        response  <- attemptRequest(newTokens.accessToken)
      } yield response

    for {
      tokenStoreValue <- tokenStore.get
      response        <- tokenStoreValue match {
        case Some(tokens) =>
          attemptRequest(tokens.accessToken).flatMap { response =>
            if (response.status == Status.Unauthorized) refreshAndRetry(tokens) else ZIO.succeed(response)
          }
        case None         =>
          login *> makeAuthenticatedRequest(request)
      }
    } yield response
  }

  override def logout: IO[Throwable, Unit] =
    tokenStore.get.map(_.map(_.refreshToken)).flatMap {
      case Some(refreshToken) =>
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
      case None                =>
        ZIO.unit
    }

}
