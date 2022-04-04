package example

import zhttp.http.Headers
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, Schedule, URIO}

object AuthenticationClient extends App {

  /**
   * This example is trying to access a protected route in AuthenticationServer
   * by first making a login request to obtain a jwt token and use it to access
   * a protected route. Run AuthenticationServer before running this example.
   */
  val url = "http://localhost:8090"
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  val program = for {
    client   <- Client.makeForDestination[Any]("localhost", 8090)
    // Making a login request to obtain the jwt token. In this example the password should be the reverse string of username.
    _        <- Client
      .requestWithPool(s"${url}/login/username/emanresu", client = client)
      .flatMap(_.bodyAsString)
      .repeat(Schedule.recurs(100))
    token    <- Client.requestWithPool(s"${url}/login/username/emanresu", client = client).flatMap(_.bodyAsString)
    // Once the jwt token is procured, adding it as a Barer token in Authorization header while accessing a protected route.
    response <- Client.requestWithPool(
      s"${url}/user/userName/greet",
      headers = Headers.bearerAuthorizationHeader(token),
      client = client,
    )
    body     <- response.bodyAsString
    _        <- zio.console.putStrLn(body)
    _ = client.release()
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
