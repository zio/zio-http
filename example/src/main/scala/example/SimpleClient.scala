package example

import zhttp.service.Client
import zio._

object SimpleClient extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url)
    data <- res.bodyAsString
    _    <- Console.printLine(data)
  } yield ()

  override val run = program

}
