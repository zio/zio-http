package example

import zio._
import zio.http.Client
import zio.http.service.{ChannelFactory, EventLoopGroup}

object SimpleClient extends ZIOAppDefault {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url)
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  override val run = program.provide(env)

}
