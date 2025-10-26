package example.auth.bearer.oauth

import example.auth.bearer.oauth.core._
import zio._
import zio.http._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

object AuthenticationClient extends ZIOAppDefault {

  private val serverUrl  = "http://localhost:8080"
  private val profileUrl = URL.decode(s"$serverUrl/profile/me").toOption.get

  val program: ZIO[Client & OAuthClient, Throwable, Unit] =
    for {
      _        <- Console.printLine("=== GitHub OAuth Authentication ===")
      client   <- ZIO.service[OAuthClient]
      response <- client.makeAuthenticatedRequest(Request.get(profileUrl))

      _ <- response.status match {
        case Status.Ok =>
          for {
            user <- response.body.to[GitHubUser]
            _    <- Console.printLine("User Profile:")
            _    <- Console.printLine(s"  ID: ${user.id}")
            _    <- Console.printLine(s"  Username: ${user.login}")
            _    <- Console.printLine(s"  Name: ${user.name.getOrElse("N/A")}")
            _    <- Console.printLine(s"  Email: ${user.email.getOrElse("N/A")}")
            _    <- Console.printLine(s"  Avatar: ${user.avatar_url}")
          } yield ()
        case _         =>
          for {
            body <- response.body.asString
            _    <- Console.printLine(s"Error: ${response.status}")
            _    <- Console.printLine(s"Response: $body")
          } yield ()
      }
      _ <- Console.printLine("\n=== Authentication Demo Complete ===")
    } yield ()

  override val run = program.provide(Client.default, OAuthClient.live)
}
