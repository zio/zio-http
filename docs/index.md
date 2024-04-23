---
id: index
title: "Introduction to ZIO Http"
sidebar_label: "ZIO Http"
---

ZIO HTTP is a scala library for building http apps. It is powered by ZIO and [Netty](https://netty.io/) and aims at being the defacto solution for writing, highly scalable and performant web applications using idiomatic Scala.

@PROJECT_BADGES@

## Installation

Setup via `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "@VERSION@"
```

**NOTES ON VERSIONING:**

- Older library versions `1.x` or `2.x` with organization `io.d11` of ZIO Http are derived from Dream11, the organization that donated ZIO Http to the ZIO organization in 2022.
- Newer library versions, starting in 2023 and resulting from the [ZIO organization](https://dev.zio) started with `0.0.x`, reaching `1.0.0` release candidates in April of 2023

## Getting Started

ZIO HTTP provides a simple and expressive API for building HTTP applications. It supports both server and client-side APIs. 

ZIO HTTP is designed in terms of **HTTP as function**, where both server and client are a function from `Request` to `Response`.

### Greeting Server

The following example demonstrates how to build a simple greeting server. It contains 2 routes: one on the root
path, it responds with a fixed string, and one route on the path `/greet` that responds with a greeting message
based on the query parameter `name`.

```scala mdoc:silent
import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {
  val app =
    Routes(
      Method.GET / "" -> handler(Response.text("Greetings at your service")),
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryParamToOrElse("name", "World")
        Response.text(s"Hello $name!")
      }
    ).toHttpApp

  def run = Server.serve(app).provide(Server.default)
}
```

### Greeting Client

The following example demonstrates how to call the greeting server using the ZIO HTTP client:

```scala mdoc:compile-only
import zio._
import zio.http._

object GreetingClient extends ZIOAppDefault {

  val app =
    for {
      client   <- ZIO.serviceWith[Client](_.host("localhost").port(8080))
      request  =  Request.get("greet").addQueryParam("name", "John")
      response <- client.request(request)
      _        <- response.body.asString.debug("Response")
    } yield ()

  def run = app.provide(Client.default, Scope.default)
}
```

## Watch Mode

We can use the [sbt-revolver] plugin to start the server and run it in watch mode using `~ reStart` command on the SBT console.

[sbt-revolver]: https://github.com/spray/sbt-revolver
