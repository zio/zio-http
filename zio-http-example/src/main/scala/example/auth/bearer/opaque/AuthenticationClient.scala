package example.auth.bearer.opaque

import zio._
import zio.http._

object AuthenticationClient extends ZIOAppDefault {
  val url = "http://localhost:8080"

  val loginUrl   = URL.decode(s"$url/login").toOption.get
  val profileUrl = URL.decode(s"$url/profile/me").toOption.get
  val logoutUrl  = URL.decode(s"$url/logout").toOption.get

  val program = for {
    client <- ZIO.service[Client]
    token  <- client
      .batched(
        Request
          .post(
            loginUrl,
            Body.fromURLEncodedForm(
              Form(
                FormField.simpleField("username", "john"),
                FormField.simpleField("password", "password123"),
              ),
            ),
          ),
      )
      .flatMap(_.body.asString)
      .tapError(error => Console.printLine(s"Login failed: $error"))

    response <- client
      .batched(Request.get(profileUrl).addHeader(Header.Authorization.Bearer(token)))
      .tapError(error => Console.printLine(s"Protected route access failed: $error"))

    body <- response.body.asString
    _    <- Console.printLine(s"Protected route response: $body")

    _              <- Console.printLine("Logging out...")
    logoutResponse <- client
      .batched(Request.post(logoutUrl, Body.empty).addHeader(Header.Authorization.Bearer(token)))
      .tapError(error => Console.printLine(s"Logout failed: $error"))

    logoutBody <- logoutResponse.body.asString
    _          <- Console.printLine(s"Logout response: $logoutBody")

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
