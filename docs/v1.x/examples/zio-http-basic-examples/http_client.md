# HTTP Client
```scala
import zio.http.model.headers.Headers
import zio.http.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers.host("sports.api.decathlon.com")

  val program = for {
    res  <- Client.request(url, headers)
    data <- res.bodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.exitCode.provideCustomLayer(env)

}
```