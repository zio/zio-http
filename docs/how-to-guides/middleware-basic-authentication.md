---
id: middleware-basic-authentication
title: "Middleware Basic Authentication Example"
---

This code demonstrates how to configure an HTTP server with basic authentication. Basic authentication is a method of enforcing access control to an HTTP server by requiring clients to provide valid credentials.


## Code 

```scala mdoc:silent
import zio._

import zio.http._
import zio.http.Middleware.basicAuth
import zio.http.codec.PathCodec.string

object BasicAuth extends ZIOAppDefault {

  // Http app that requires a JWT claim
  val user: HttpApp[Any] = Routes(
    Method.GET / "user" / string("name") / "greet" ->
      handler { (name: String, req: Request) =>
        Response.text(s"Welcome to the ZIO party! ${name}")
      },
  ).toHttpApp

  // Composing all the HttpApps together
  val app: HttpApp[Any] = user @@ basicAuth("admin", "admin")

  // Run it like any simple app
  val run = Server.serve(app).provide(Server.default)
}
```