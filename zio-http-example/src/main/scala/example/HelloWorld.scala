package example

import zio._
import zio.http._
import zio.http.model.{Method, Request, Response}

object HelloWorld extends ZIOAppDefault {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }

  // Run it like any simple app
  override val run =
    Server.start(8090, app)
}
