package example

import zhttp.http.Header
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO, console}

object SimpleClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = List(Header.host("sports.api.decathlon.com"))

  val program = for {
    res  <- Client.request(url, headers)
    data <- res.getBodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
