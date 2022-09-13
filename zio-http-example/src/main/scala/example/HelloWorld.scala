package example

import zio._
import zio.http._

object HelloWorld extends ZIOAppDefault {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }

  // Run it like any simple app

  val theApp = Server2.Server.serve(
    app
  ).provide(Server2.ServerConfig.default >>> Server2.Server.live)
  override val run =  theApp



}
