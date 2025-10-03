package example.auth.bearer.oauth

import zio._
import zio.json._

import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

import zio.http._

import example.auth.bearer.oauth.core._

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
      _ <- Console.printLine("\n=== Authentication Demo Complete ===")
    } yield ()

  override val run = program.provide(Client.default, OAuthClient.live)
}
