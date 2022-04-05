package example

import zhttp.http.Headers
import zhttp.service.{ChannelFactory, ClientWithPool, EventLoopGroup}
import zio.{App, ExitCode, URIO, ZIO}

object AuthenticationClient extends App {

  /**
   * This example is trying to access a protected route in AuthenticationServer
   * by first making a login request to obtain a jwt token and use it to access
   * a protected route. Run AuthenticationServer before running this example.
   */
  val url = "http://localhost:8090"
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  val program = for {
    client   <- ClientWithPool.make[Any]("localhost", 8090)
    token    <- client.requestWithPool(s"${url}/login/username/emanresu", isWebSocket = false).flatMap(_.bodyAsString)
    // Making a login request to obtain the jwt token. In this example the password should be the reverse string of username.
    _        <- ZIO.foreachParN_(10)(1 to 1000) { id =>
      println(id)
      client
        .requestWithPool(s"${url}/login/username/emanresu", isWebSocket = false)
        .flatMap(_.bodyAsString)
        .flatMap(data => ZIO.debug(data))
    }
    // Once the jwt token is procured, adding it as a Barer token in Authorization header while accessing a protected route.
    response <- client.requestWithPool(
      s"${url}/user/userName/greet",
      headers = Headers.bearerAuthorizationHeader(token),
      isWebSocket = false,
    )
    body     <- response.bodyAsString
    _        <- zio.console.putStrLn(body)
    // _ = client.release()
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
