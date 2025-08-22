package example.auth.session.cookie

import zio._

import zio.http._

object CookieAuthenticationClient extends ZIOAppDefault {

  /**
   * This example is trying to access a protected route by first making a login
   * request to obtain a session cookie and use it to access a protected route.
   * Run CookieAuthenticationServer before running this example.
   */
  private val SERVER_URL = "http://localhost:8080"
  private val loginUrl   = URL.decode(s"$SERVER_URL/login").toOption.get
  private val profileUrl = URL.decode(s"$SERVER_URL/profile/me").toOption.get
  private val logoutUrl  = URL.decode(s"$SERVER_URL/logout").toOption.get

  val program      =
    for {
      // Making a login request to obtain the session cookie. In this example the password should be the reverse string of username.
      _             <- Console.printLine("Making login request...")
      loginResponse <- ZClient.batched(
        Request
          .post(
            url = loginUrl,
            body = Body.fromURLEncodedForm(
              Form(
                FormField.simpleField("username", "john"),
                FormField.simpleField("password", "password123"),
              ),
            ),
          ),
      )
      loginBody     <- loginResponse.body.asString
      _             <- Console.printLine(s"Login response: $loginBody")
      cookie = loginResponse.headers(Header.SetCookie).head.value.toRequest
      _              <- Console.printLine("Accessing protected route...")
      greetResponse  <- ZClient.batched(Request.get(profileUrl).addCookie(cookie))
      greetBody      <- greetResponse.body.asString
      _              <- Console.printLine(s"Protected route response: $greetBody")
      // Demonstrate logout
      _              <- Console.printLine("Logging out...")
      logoutResponse <- ZClient.batched(Request.get(logoutUrl).addCookie(cookie))
      logoutBody     <- logoutResponse.body.asString
      _              <- Console.printLine(s"Logout response: $logoutBody")
      // Try to access protected route again after logout (should fail)
      _              <- Console.printLine("Trying to access protected route after logout...")
      finalResponse  <- ZClient.batched(Request.get(profileUrl).addCookie(cookie))
      finalBody      <- finalResponse.body.asString
      _              <- Console.printLine(s"Final response: $finalBody")
      _              <- Console.printLine(s"Final response status: ${finalResponse.status}")
    } yield ()
  override val run = program.provide(Client.default)
}
