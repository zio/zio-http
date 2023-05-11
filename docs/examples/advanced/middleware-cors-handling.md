---
id: middleware-cors-handling
title: "Middleware CORS Handling Example"
sidebar_label: "Middleware CORS Handling"
---

```scala mdoc:silent
import zio._

import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin, Origin}
import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.http.internal.middlewares.Cors.CorsConfig

object HelloWorldWithCORS extends ZIOAppDefault {

  // Create CORS configuration
  val config: CorsConfig =
    CorsConfig(
      allowedOrigin = {
        case origin@Origin.Value(_, host, _) if host == "dev" => Some(AccessControlAllowOrigin.Specific(origin))
        case _ => None
      },
      allowedMethods = AccessControlAllowMethods(Method.PUT, Method.DELETE),
    )

  // Create HTTP route with CORS enabled
  val app: HttpApp[Any, Nothing] =
    Http.collect[Request] {
      case Method.GET -> Root / "text" => Response.text("Hello World!")
      case Method.GET -> Root / "json" => Response.json("""{"greetings": "Hello World!"}""")
    } @@ cors(config)

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
```