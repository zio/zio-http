package example

import zhttp.service.{Client, EventLoopGroup}
import zio._

object SimpleClient extends ZIOAppDefault {
  val env = EventLoopGroup.auto()
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    client <- Client.make[Any]()
    res    <- client.request(url)
    data   <- res.body.asString
    _      <- Console.printLine(data)
  } yield ()

  override val run = program.provide(env)

}
