# HTTP Client
```scala
import zhttp.http.Headers
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers.host("sports.api.decathlon.com")

  val program = for {
    res  <- Client.request(url, headers)
    data <- res.bodyAsString
    _    <- Console.printLine { data }
  } yield ()

  override def run(args: List[String]): UIO[ExitCode] =
    program.exitCode.provideLayer(env)

}
```