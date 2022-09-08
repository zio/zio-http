package example

import zhttp.ZHttpDefaultClientApp
import zhttp.service.Client
import zio._

object SimpleClient extends ZHttpDefaultClientApp {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url)
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  override def run = program

}
