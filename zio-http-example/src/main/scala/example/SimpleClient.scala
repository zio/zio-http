//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object SimpleClient extends ZIOAppDefault {
  val url = URL.decode("https://jsonplaceholder.typicode.com/todos").toOption.get

  val program = for {
    client <- ZIO.service[Client]
    res    <- client.url(url).batched(Request.get("/"))
    data   <- res.body.asString
    _      <- Console.printLine(data)
  } yield ()

  override val run = program.provide(Client.default)

}
