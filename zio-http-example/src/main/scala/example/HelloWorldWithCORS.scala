package example

import zio._

import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.http.middleware.Cors.CorsConfig
import zio.http.model.Header.{AccessControlAllowMethods, AccessControlAllowOrigin, Origin}
import zio.http.model.Method

object HelloWorldWithCORS extends ZIOAppDefault {

  // Create CORS configuration
  val config: CorsConfig =
    CorsConfig(
      allowedOrigin = {
        case origin @ Origin.Value(_, host, _) if host == "dev" => Some(AccessControlAllowOrigin.Specific(origin))
        case _                                                  => None
      },
      allowedMethods = AccessControlAllowMethods(Method.PUT, Method.DELETE),
    )

  // Create HTTP route with CORS enabled
  val app: HttpApp[Any, Nothing] =
    Http.collect[Request] {
      case Method.GET -> !! / "text" => Response.text("Hello World!")
      case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
    } @@ cors(config)

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
