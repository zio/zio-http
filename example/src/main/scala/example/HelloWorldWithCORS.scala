package example

import zhttp.http.Middleware.cors
import zhttp.http.{CORSConfig, HttpApp, Method, Response, _}
import zhttp.service.Server
import zio._

object HelloWorldWithCORS extends ZIOAppDefault {

  // Create CORS configuration
  val config: CORSConfig =
    CORSConfig(allowedOrigins = _ == "dev", allowedMethods = Some(Set(Method.PUT, Method.DELETE)))

  // Create HTTP route with CORS enabled
  val app: HttpApp[Any, Nothing] =
    Http.collect[Request] {
      case Method.GET -> !! / "text" => Response.text("Hello World!")
      case Method.GET -> !! / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
    } @@ cors(config)

  // Run it like any simple app
  val run =
    Server.start(8090, app.silent)
}
