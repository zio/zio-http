package example.auth.bearer.opaque

import zio._
import zio.http._

object AuthenticationClient extends ZIOAppDefault {

  /**
   * This example is trying to access a protected route in AuthenticationServer
   * by first making a login request to obtain an opaque token and use it to
   * access a protected route. Run AuthenticationServer before running this
   * example.
   */
  val url = "http://localhost:8080"

  val loginUrl  = URL.decode(s"$url/login").toOption.get
  val profileUrl  = URL.decode(s"$url/profile/me").toOption.get
  val logoutUrl = URL.decode(s"$url/logout").toOption.get

  val program = for {
    client <- ZIO.service[Client]
    // Making a login request to obtain the opaque token. In this example the password should be the reverse string of username.
    token  <- client
      .batched(
        Request
          .get(loginUrl)
          .withBody(
            Body.fromMultipartForm(
              Form(
                FormField.simpleField("username", "John"),
                FormField.simpleField("password", "nhoJ"),
              ),
              Boundary("boundary123"),
            ),
          ),
      )
      .flatMap(_.body.asString)
      .tapError(error => Console.printLine(s"Login failed: $error"))

    _ <- Console.printLine(s"Received opaque token: $token ...")

    // Once the opaque token is procured, adding it as a Bearer token in Authorization header while accessing a protected route.
    response <- client
      .batched(Request.get(profileUrl).addHeader(Header.Authorization.Bearer(token)))
      .tapError(error => Console.printLine(s"Protected route access failed: $error"))

    body <- response.body.asString
    _    <- Console.printLine(s"Protected route response: $body")

    // Optional: Demonstrate logout functionality
    _              <- Console.printLine("Logging out...")
    logoutResponse <- client
      .batched(Request.post(logoutUrl, Body.empty).addHeader(Header.Authorization.Bearer(token)))
      .tapError(error => Console.printLine(s"Logout failed: $error"))

    logoutBody <- logoutResponse.body.asString
    _          <- Console.printLine(s"Logout response: $logoutBody")

    // Try to access protected route again after logout (should fail)
    _ <- Console.printLine("Trying to access protected route after logout...")
    _ <- client
      .batched(Request.get(profileUrl).addHeader(Header.Authorization.Bearer(token)))
      .tapBoth(
        error => Console.printLine(s"Failure after logout: $error"),
        response => response.body.asString.flatMap(body => Console.printLine(s"Unexpected success: $body")),
      )

  } yield ()

  override val run = program.provide(Client.default)

}
