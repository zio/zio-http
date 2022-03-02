package example

import zhttp.http.Headers
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO}

object AuthenticationClient extends App {
  // start AuthenticationServer before running this client
  val url = "http://localhost:8090"
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  val program = for {
    token    <- Client.request(s"${url}/login/username/emanresu").flatMap(_.bodyAsString)
    response <- Client.request(s"${url}/user/userName/greet", headers = Headers.bearerAuthorizationHeader(token))
    body     <- response.bodyAsString
    _        <- zio.console.putStrLn(body)
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
