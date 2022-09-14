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
  override val run = Server.serve(app).provide(Server.default)

//  val config = ServerConfig.Config().withPort(8090)
//  val configLayer = ServerConfig.live(config)
//  override val run = Server.serve(app).provide(configLayer >>> Server.live)

}
