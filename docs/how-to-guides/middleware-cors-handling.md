---
id: middleware-cors-handling
title: "Middleware CORS Handling Example"
---

This code provides a practical example of setting up an HTTP server with Cross-Origin Resource Sharing (CORS) enabled. CORS is a mechanism that allows web browsers to safely access resources from different origins.

```scala mdoc:silent
import zio._

import zio.http._
import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin, Origin}
import zio.http.Middleware.{cors, CorsConfig}

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
```