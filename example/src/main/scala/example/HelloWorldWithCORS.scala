package example

import zhttp.http.Middleware.cors
import zhttp.http._
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object HelloWorldWithCORS extends App {

  // Create CORS configuration
  val config: CorsConfig =
    CorsConfig(allowedOrigins = _ == "dev", allowedMethods = Some(Set(Method.PUT, Method.DELETE)))

  // Create HTTP route with CORS enabled
  val app: HttpApp[Any, Nothing] =
    Http.collect[Request] {
      case Method.GET -> !! / "text" => Response.text("Hello World!")
      case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
    } @@ cors(config)

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
