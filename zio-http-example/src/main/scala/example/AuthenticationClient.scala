//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object AuthenticationClient extends ZIOAppDefault {

  /**
   * This example is trying to access a protected route in AuthenticationServer
   * by first making a login request to obtain a jwt token and use it to access
   * a protected route. Run AuthenticationServer before running this example.
   */
  val url = "http://localhost:8080"

  val loginUrl = URL.decode(s"${url}/login").toOption.get
  val greetUrl = URL.decode(s"${url}/profile/me").toOption.get

  val program = for {
    client   <- ZIO.service[Client]
    // Making a login request to obtain the jwt token. In this example the password should be the reverse string of username.
    token    <- client
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
    // Once the jwt token is procured, adding it as a Bearer token in Authorization header while accessing a protected route.
    response <- client.batched(Request.get(greetUrl).addHeader(Header.Authorization.Bearer(token)))
    body     <- response.body.asString
    _        <- Console.printLine(body)
  } yield ()

  override val run = program.provide(Client.default)

}
