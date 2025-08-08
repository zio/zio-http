package example.auth.digest

import example.auth.digest.core._
import zio.Config.Secret
import zio._
import zio.http._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

import java.net.URI

case class DigestAuthState(
  realm: String,
  nonce: String,
  algorithm: DigestAlgorithm,
  supportedQop: Set[QualityOfProtection],
  opaque: Option[String],
  nc: Ref[NC], // Mutable counter for nonce count
)

// ZIO Service for managing digest authentication
trait DigestAuthClient {
  def makeRequest(request: Request): ZIO[Any, Throwable, Response]
}

object DigestAuthClient {
  final case class DigestAuthClientImpl(
    authStateRef: Ref[Option[DigestAuthState]],
    client: Client,
    digestService: DigestService,
    nonceService: NonceService,
    username: String,
    password: Secret,
  ) extends DigestAuthClient {
    override def makeRequest(request: Request): ZIO[Any, Throwable, Response] =
      for {
        authenticatedRequest <- authenticate(request)
        response             <- client.batched(authenticatedRequest)

        finalResponse <-
          if (response.status == Status.Unauthorized) {
            for {
              _             <- ZIO.debug("Unauthorized response received!")
              _             <- handleUnauthorized(response)
              retryRequest  <- authenticate(request)
              retryResponse <- client.batched(retryRequest)
              _             <- ZIO.debug("Retrying request with updated authentication headers")
            } yield retryResponse
          } else ZIO.succeed(response)
      } yield finalResponse

    // Helper methods
    private def selectQop(request: Request, supportedQop: Set[QualityOfProtection]): QualityOfProtection =
      if (!request.body.isEmpty && supportedQop.contains(QualityOfProtection.AuthInt))
        QualityOfProtection.AuthInt
      else
        QualityOfProtection.Auth

    private def authenticate(request: Request): ZIO[Any, Throwable, Request] =
      authStateRef.get.flatMap {
        case None =>
          ZIO.debug(s"No cached digest!") *>
            ZIO.debug("Sending request without auth header to get a fresh challenge")
          ZIO.succeed(request)

        case Some(authState) =>
          for {
            _         <- ZIO.debug(s"Cached digest challenge found, use it to compute the digest response!")
            cnonce    <- nonceService.generateNonce
            currentNc <- authState.nc.updateAndGet(nc => NC(nc.value + 1))
            selectedQop = selectQop(request, authState.supportedQop)
            uri         = URI.create(request.url.path.toString)
            body <- request.body.asString
              .map(Some(_))
              .orElseFail(
                new RuntimeException("Failed to read request body as string"),
              )

            response <- digestService.computeResponse(
              username = username,
              realm = authState.realm,
              uri = uri,
              algorithm = authState.algorithm,
              qop = selectedQop,
              cnonce = cnonce,
              nonce = authState.nonce,
              nc = currentNc,
              password = password,
              method = request.method,
              body = body,
            )

            authHeader = Header.Authorization.Digest(
              response = response,
              username = username,
              realm = authState.realm,
              nonce = authState.nonce,
              uri = uri,
              algorithm = authState.algorithm.toString,
              qop = selectedQop.toString,
              nc = currentNc.value,
              cnonce = cnonce,
              userhash = false,
              opaque = authState.opaque.getOrElse(""),
            )
            _ <- ZIO.debug(s"nonce: ${authState.nonce}")
            _ <- ZIO.debug(s"nc: $currentNc")
            _ <- ZIO.debug(s"response: $response")
          } yield request.addHeader(authHeader)
      }

    private def handleUnauthorized(response: Response): ZIO[Any, Throwable, Unit] =
      response.header(Header.WWWAuthenticate) match {
        case Some(header: Header.WWWAuthenticate.Digest) =>
          for {
            _     <- ZIO.debug(s"Received a new WWW-Authenticate Digest challenge")
            realm <- ZIO
              .fromOption(header.realm)
              .orDieWith(_ => new RuntimeException("Missing required 'realm' in WWW-Authenticate header"))
            nonce <- ZIO
              .fromOption(header.nonce)
              .orDieWith(_ => new RuntimeException("Missing required 'nonce' in WWW-Authenticate header"))

            algorithmValue = DigestAlgorithm.fromString(header.algorithm).getOrElse(DigestAlgorithm.MD5)
            supportedQop   = QualityOfProtection.fromChallenge(header.qop.getOrElse("auth"))

            ncRef <- Ref.make(NC(0))

            newAuthState = DigestAuthState(
              realm = realm,
              nonce = nonce,
              algorithm = algorithmValue,
              supportedQop = supportedQop,
              opaque = header.opaque,
              nc = ncRef,
            )

            _ <- ZIO.debug(s"Caching digest challenge")
            _ <- authStateRef.set(Some(newAuthState))
          } yield ()
        case Some(other)                                 =>
          ZIO.die(new RuntimeException(s"Unexpected WWW-Authenticate header type: $other"))

        case None =>
          ZIO.fail(new Exception("No WWW-Authenticate header found"))
      }

  }

  def live(
    username: String,
    password: Secret,
  ): ZLayer[Client with DigestService with NonceService, Nothing, DigestAuthClient] =
    ZLayer {
      for {
        client        <- ZIO.service[Client]
        digestService <- ZIO.service[DigestService]
        nonceService  <- ZIO.service[NonceService]
        authStateRef  <- Ref.make[Option[DigestAuthState]](None)
      } yield DigestAuthClientImpl(authStateRef, client, digestService, nonceService, username, password)
    }
}

object DigestAuthenticationClient extends ZIOAppDefault {
  private val url      = "http://localhost:8080"
  private val USERNAME = "john"
  private val PASSWORD = Secret("password123")

  val program: ZIO[Client with DigestAuthClient, Throwable, Unit] =
    for {
      authClient <- ZIO.service[DigestAuthClient]

      profileEndpoint <- ZIO.fromEither(URL.decode(s"$url/profile/me"))
      emailEndpoint   <- ZIO.fromEither(URL.decode(s"$url/profile/email"))

      _         <- ZIO.debug("\nFirst call: GET /profile/me")
      response1 <- authClient.makeRequest(Request(method = Method.GET, url = profileEndpoint))
      body1     <- response1.body.asString
      _         <- ZIO.debug(s"Received response: $body1")

      _         <- ZIO.debug("\nSecond call: GET /profile/me")
      response2 <- authClient.makeRequest(Request(method = Method.GET, url = profileEndpoint))
      body2     <- response2.body.asString
      _         <- ZIO.debug(s"Received response: $body2")

      _ <- ZIO.debug("\nThird call: PUT /profile/email")
      email        = UpdateEmailRequest("my-new-email@example.com")
      emailRequest = Request(method = Method.PUT, url = emailEndpoint, body = Body.from(email))
      response3 <- authClient.makeRequest(emailRequest)
      body3     <- response3.body.asString
      _         <- ZIO.debug(s"Received response: $body3")

      _         <- ZIO.debug("\nFourth call: GET /profile/me")
      response4 <- authClient.makeRequest(Request(method = Method.GET, url = profileEndpoint))
      body4     <- response4.body.asString
      _         <- ZIO.debug(s"Received response: $body4")
    } yield ()

  override val run = program.provide(
    Client.default,
    NonceService.live,
    DigestService.live,
    DigestAuthClient.live(USERNAME, PASSWORD),
  )
}
