package example

import zhttp.http.Headers
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO}

object AuthenticationClient extends App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  val program = for {
    res1  <- Client.request("http://localhost:8090/login/username/emanresu")
    data1 <- res1.bodyAsString
    res2  <- Client.request(
      "http://localhost:8090/user/userName/greet",
      headers = Headers.bearerAuthorizationHeader(data1),
    )
    data2 <- res2.bodyAsString
    _ = println(data2)
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
