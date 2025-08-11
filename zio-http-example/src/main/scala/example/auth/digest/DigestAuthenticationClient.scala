package example.auth.digest

import java.net.URI

import zio.Config.Secret
import zio._

import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

import zio.http._

import example.auth.digest.core.DigestAuthError.UnsupportedAuthHeader
import example.auth.digest.core._

trait DigestAuthClient {
  def makeRequest(request: Request): ZIO[Any, DigestAuthError, Response]
}

object DigestAuthClient {
  final case class DigestAuthClientImpl(
    challengeRef: Ref[Option[DigestChallenge]],
    ncRef: Ref[NC],
    client: Client,
    digestService: DigestService,
    nonceService: NonceService,
    username: String,
    password: Secret,
  ) extends DigestAuthClient {
    override def makeRequest(request: Request): ZIO[Any, DigestAuthError, Response] =
      for {
        authenticatedRequest <- authenticate(request)
        response             <- client.batched(authenticatedRequest).orDie

        finalResponse <-
          if (response.status == Status.Unauthorized) {
            for {
              _             <- ZIO.debug("Unauthorized response received!")
              _             <- handleUnauthorized(response)
              retryRequest  <- authenticate(request)
              retryResponse <- client.batched(retryRequest).orDie
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

    private def authenticate(request: Request): ZIO[Any, Nothing, Request] =
      challengeRef.get.flatMap {
        case None =>
          ZIO.debug(s"No cached digest!") *>
            ZIO.debug("Sending request without auth header to get a fresh challenge") *>
            ZIO.succeed(request)

        case Some(challenge) =>
          for {
            _      <- ZIO.debug(s"Cached digest challenge found, use it to compute the digest response!")
            cnonce <- nonceService.generateNonce
            nc     <- ncRef.updateAndGet(nc => NC(nc.value + 1))
            selectedQop = selectQop(request, challenge.qop)
            _ <- ZIO.debug(s"Selected QOP: $selectedQop")
            uri = URI.create(request.url.path.toString)
            body <- request.body.asString
              .map(Some(_))
              .orDie

            response <- digestService.computeResponse(
              username = username,
              realm = challenge.realm,
              uri = uri,
              algorithm = challenge.algorithm,
              qop = selectedQop,
              cnonce = cnonce,
              nonce = challenge.nonce,
              nc = nc,
              password = password,
              method = request.method,
              body = body,
            )

            authHeader = Header.Authorization.Digest(
              response = response,
              username = username,
              realm = challenge.realm,
              nonce = challenge.nonce,
              uri = uri,
              algorithm = challenge.algorithm.toString,
              qop = selectedQop.toString,
              nc = nc.value,
              cnonce = cnonce,
              userhash = false,
              opaque = challenge.opaque.getOrElse(""),
            )
            _ <- ZIO.debug(s"nonce: ${challenge.nonce}")
            _ <- ZIO.debug(s"nc: $nc")
            _ <- ZIO.debug(s"response: $response")
          } yield request.addHeader(authHeader)
      }

    private def handleUnauthorized(response: Response): ZIO[Any, DigestAuthError, Unit] =
      response.header(Header.WWWAuthenticate) match {
        case Some(header: Header.WWWAuthenticate.Digest) =>
          for {
            _            <- ZIO.debug(s"Received a new WWW-Authenticate Digest challenge")
            newChallenge <- DigestChallenge.fromHeader(header)
            _            <- ZIO.debug(s"Caching digest challenge")
            _            <- challengeRef.set(Some(newChallenge))
            _            <- ncRef.set(NC(0)) // Reset nonce count
          } yield ()
        case _                                           =>
          ZIO.fail(UnsupportedAuthHeader("Expected WWW-Authenticate header"))
      }

  }

  def live(
    username: String,
    password: Secret,
  ): ZLayer[Client with DigestService with NonceService, Nothing, DigestAuthClient] =
    ZLayer {
      for {
        challengeRef  <- Ref.make[Option[DigestChallenge]](None)
        nc            <- Ref.make(NC(0))
        client        <- ZIO.service[Client]
        digestService <- ZIO.service[DigestService]
        nonceService  <- ZIO.service[NonceService]
      } yield DigestAuthClientImpl(challengeRef, nc, client, digestService, nonceService, username, password)
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
