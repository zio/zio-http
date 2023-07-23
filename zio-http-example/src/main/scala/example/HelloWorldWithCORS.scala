package example

import zio._

import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin, Origin}
import zio.http.RoutesAspect.{CorsConfig, cors}
import zio.http._

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
  val app: HttpApp[Any] =
    Routes(
      Method.GET / "text" -> handler(Response.text("Hello World!")),
      Method.GET / "json" -> handler(Response.json("""{"greetings": "Hello World!"}""")),
    ).toHttpApp @@ cors(config)

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
