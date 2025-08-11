package example.auth.basic

import zio._

import zio.http._

object AuthenticationClient extends ZIOAppDefault {

  /**
   * This example demonstrates how to make requests to a server with custom
   * basic authentication that passes the username to handlers. Run
   * BasicAuthServer before running this example.
   */
  val url           = "http://localhost:8080"
  val publicUrl     = URL.decode(s"$url/public").toOption.get
  val profileMeUrl  = URL.decode(s"$url/profile/me").toOption.get
  val adminUsersUrl = URL.decode(s"$url/admin/users").toOption.get

  val program =
    for {
      _ <- Console.printLine("=== Basic Authentication Client Example ===")

      // 1. Access public endpoint (no authentication required)
      _         <- Console.printLine(s"\n--- Accessing public endpoint ---")
      response1 <- ZClient.batched(Request.get(publicUrl))
      body1     <- response1.body.asString
      _         <- Console.printLine(s"Status: ${response1.status}")
      _         <- Console.printLine(s"Response: $body1")

      // 2. Access profile with john's credentials - shows username in response
      _         <- Console.printLine(s"\n--- Accessing profile/me with john's credentials ---")
      response2 <- ZClient.batched(Request.get(profileMeUrl).addHeader(Header.Authorization.Basic("john", "secret123")))
      body2     <- response2.body.asString
      _         <- Console.printLine(s"Status: ${response2.status}")
      _         <- Console.printLine(s"Response: $body2")

      // 3. Access admin endpoint with admin credentials
      _         <- Console.printLine(s"\n--- Accessing admin/users with admin credentials ---")
      response3 <- ZClient.batched(
        Request.get(adminUsersUrl).addHeader(Header.Authorization.Basic("admin", "admin123")),
      )
      body3     <- response3.body.asString
      _         <- Console.printLine(s"Status: ${response3.status}")
      _         <- Console.printLine(s"Response: $body3")

      _ <- Console.printLine("\n=== All requests completed ===")
    } yield ()

  override val run = program.provide(Client.default)
}
