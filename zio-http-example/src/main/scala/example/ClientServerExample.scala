//> using dep "dev.zio::zio-http:3.4.1"

package example
import zio._

import zio.http._
import zio.http.codec.TextBinaryCodec.fromSchema

object ClientServerExample extends ZIOAppDefault {
  val clientApp: ZIO[Client, Throwable, Unit] =
    for {
      url  <- ZIO.fromEither(URL.decode("http://localhost:8080/greet"))
      res  <- ZClient.batched(
        Request
          .get(url)
          .setQueryParams(
            Map("name" -> Chunk("ZIO HTTP")),
          ),
      )
      body <- res.bodyAs[String]
      _    <- ZIO.debug("Received response: " + body)
    } yield ()

  val run = clientApp.provide(Client.default)
}

import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {
  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryOrElse("name", "World")
        Response.text(s"Hello $name!")
      },
    )

  def run = Server.serve(routes).provide(Server.default)
}
