package example

import zio._

import zio.http._

object SimpleClient extends ZIOAppDefault {
  val url = URL.decode("http://sports.api.decathlon.com/groups/water-aerobics").toOption.get

  val program = for {
    client <- ZIO.service[Client]
    res    <- client.url(url).get("/")
    data   <- res.body.asString
    _      <- Console.printLine(data)
  } yield ()

  override val run = program.provideSome[Scope](Client.default)

}
