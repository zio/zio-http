package example.auth.digest

import DigestAuthentication._
import zio._
import zio.http._

import java.net.URI

object DigestAuthenticationClient extends ZIOAppDefault {

  val url      = "http://localhost:8080"
  val USERNAME = "john"
  val PASSWORD = "password123"

  val program: ZIO[Client, Throwable, Unit] =
    for {
      _             <- Console.printLine("=== Digest Authentication Demo ===\n")
      profileUrl    <- ZIO.fromEither(URL.decode(s"$url/profile/me"))
      // First request (expected to fail with 401)
      firstResponse <- ZClient.batched(Request(method = Method.GET, url = profileUrl))
      _             <- Console.printLine(
        s"First request status: ${firstResponse.status}",
      )

      // If we get 401, extract challenge and retry with authentication
      result <-
        if (firstResponse.status == Status.Unauthorized) {
          firstResponse.header(Header.WWWAuthenticate) match {
            case Some(header: Header.WWWAuthenticate.Digest) =>
              val uri       = URI.create(profileUrl.path.toString)
              val cnonce    = generateNonce() // Generate random client nonce
              val nc        = 1
              val realm     = header.realm.getOrElse("")
              val nonce     = header.nonce.getOrElse("")
              val algorithm = header.algorithm.getOrElse("MD5")
              val qop       = header.qop.getOrElse("auth")

              // Compute response using enhanced digestResponse
              for {
                response <- ZClient.batched(
                  Request(method = Method.GET, url = profileUrl).addHeader(
                    Header.Authorization.Digest(
                      response = digestResponse(
                        username = USERNAME,
                        realm = realm,
                        uri = uri,
                        algorithm = algorithm,
                        qop = qop,
                        cnonce = cnonce,
                        nonce = nonce,
                        nc = nc,
                        password = PASSWORD,
                        method = Method.GET,
                      ),
                      username = USERNAME,
                      realm = realm,
                      nonce = nonce,
                      uri = uri,
                      algorithm = algorithm,
                      qop = qop,
                      nc = nc,
                      cnonce = cnonce,
                      userhash = false,
                      opaque = header.opaque.getOrElse(""),
                    ),
                  ),
                )
                body     <- response.body.asString
              } yield body

            case _ =>
              ZIO.fail(new RuntimeException("No valid WWW-Authenticate header found"))
          }
        } else
          firstResponse.body.asString

      _ <- Console.printLine(s"Response: $result")
      _ <- Console.printLine("\n=== Demo completed ===")
    } yield ()

  override val run = program.provide(Client.default)
}
