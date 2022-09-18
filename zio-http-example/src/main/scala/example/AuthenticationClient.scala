package example

import zio._
import zio.http.Client
import zio.http.model.Headers

object AuthenticationClient extends ZIOAppDefault {

  /**
   * This example is trying to access a protected route in AuthenticationServer
   * by first making a login request to obtain a jwt token and use it to access
   * a protected route. Run AuthenticationServer before running this example.
   */
  val url = "http://localhost:8090"

  val program = for {
    // Making a login request to obtain the jwt token. In this example the password should be the reverse string of username.
    token    <- Client.request(s"${url}/login/username/emanresu").flatMap(_.body.asString)
    // Once the jwt token is procured, adding it as a Barer token in Authorization header while accessing a protected route.
    response <- Client.request(s"${url}/user/userName/greet", headers = Headers.bearerAuthorizationHeader(token))
    body     <- response.body.asString
    _        <- Console.printLine(body)
  } yield ()

  override val run = program.provide(Client.default, Scope.default)

}
