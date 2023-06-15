package example

import zio._

import zio.http._

object AuthenticationClient extends ZIOAppDefault {

  /**
   * This example is trying to access a protected route in AuthenticationServer
   * by first making a login request to obtain a jwt token and use it to access
   * a protected route. Run AuthenticationServer before running this example.
   */
  val url = "http://localhost:8090"

  val loginUrl = URL.decode(s"${url}/login/username/emanresu").toOption.get
  val greetUrl = URL.decode(s"${url}/user/userName/greet").toOption.get

  val program = for {
    client   <- ZIO.service[Client]
    // Making a login request to obtain the jwt token. In this example the password should be the reverse string of username.
    token    <- client(Request.get(loginUrl)).flatMap(_.body.asString)
    // Once the jwt token is procured, adding it as a Barer token in Authorization header while accessing a protected route.
    response <- client(Request.get(greetUrl).addHeader(Header.Authorization.Bearer(token)))
    body     <- response.body.asString
    _        <- Console.printLine(body)
  } yield ()

  override val run = program.provide(Client.default, Scope.default)

}
