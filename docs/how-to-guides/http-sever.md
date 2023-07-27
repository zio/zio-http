---
id: http-sever
title:  HTTP server 
---


# Simple http sever 

This example demonstrates the creation of a simple HTTP server in zio-http with ZIO.


## `build.sbt` Setup 

```scala
scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-http" % "3.0.0-RC2"
)
```


## code 

```scala
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