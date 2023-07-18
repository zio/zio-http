---
id: http-server
title: Http Server Example
sidebar_label: Http Server
---

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