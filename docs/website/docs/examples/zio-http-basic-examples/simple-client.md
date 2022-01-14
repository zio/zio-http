# Simple HTTP Client

```scala
import zhttp.http.Headers
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends ZIOAppDefault {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers.host("sports.api.decathlon.com")

  val program = for {
    res  <- Client.request(url, headers)
    data <- res.getBodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run = 
    program.provide(env)

}
```