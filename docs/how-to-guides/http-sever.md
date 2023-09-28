---
id: http-sever
title:  HTTP server 
---


# Simple http sever 

This example demonstrates the creation of a simple HTTP server in zio-http with ZIO.

## code 

```scala mdoc:silent
import zio._

import zio.http._

object HelloWorld extends ZIOAppDefault {
  val textRoute =
    Method.GET / "text" -> handler(Response.text("Hello World!"))

  val jsonRoute =
    Method.GET / "json" -> handler(Response.json("""{"greetings": "Hello World!"}"""))

  // Create HTTP route
  val app = Routes(textRoute, jsonRoute).toHttpApp

  // Run it like any simple app
  override val run = Server.serve(app).provide(Server.default)
}

```
<br>
<br>