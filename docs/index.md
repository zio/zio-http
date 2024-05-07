---
id: index
title: "Introduction to ZIO HTTP"
sidebar_label: "ZIO HTTP"
---

ZIO HTTP is a scala library for building http apps. It is powered by ZIO and [Netty](https://netty.io/) and aims at being the defacto solution for writing, highly scalable and performant web applications using idiomatic Scala.

@PROJECT_BADGES@

## Introduction

ZIO HTTP provides a simple and expressive API for building both server and client-side applications. ZIO HTTP is designed in terms of **HTTP as function**, where both server and client are a function from `Request` to `Response`.

Some of the key features of ZIO HTTP are:

**ZIO Native**: ZIO HTTP is built atop ZIO, a type-safe, composable, and asynchronous effect system for Scala. It inherits all the benefits of ZIO, including testability, composability, and type safety.
**Cloud-Native**: ZIO HTTP is designed for cloud-native environments and supports building highly scalable and performant web applications. Built atop ZIO, it features built-in support for concurrency, parallelism, resource management, error handling, structured logging, configuration management, and metrics instrumentation.
**Declarative Endpoints**: The API offers a declarative approach to defining HTTP endpoints. Each endpoint can be described by its inputs and outputs, expressing the shape of the endpoint.
**Middleware Support**: ZIO HTTP offers middleware support for incorporating cross-cutting concerns such as logging, metrics, authentication, and more into your services.
**Error Handling**: Built-in support exists for handling errors at the HTTP layer, distinguishing between handled and unhandled errors.
**WebSockets**: Built-in support for WebSockets allows for the creation of real-time applications using ZIO HTTP.
**Testkit**: ZIO HTTP provides first-class testing utilities that facilitate test writing without requiring a live server instance.
**Interoperability**: Interoperability with existing Scala/Java libraries is provided, enabling seamless integration with functionality from the Scala/Java ecosystem through the importation of blocking and non-blocking operations.
**JSON and Binary Codecs**: Built-in support for ZIO Schema enables encoding and decoding of request/response bodies, supporting various data types including JSON, Protobuf, Avro, and Thrift.
**Template System**: A built-in DSL facilitates writing HTML templates using Scala code.
**OpenAPI Support**: Built-in support is available for generating OpenAPI documentation for HTTP applications, and conversely, for generating HTTP endpoints from OpenAPI documentation.
**ZIO HTTP CLI**: Command-line applications can be built to interact with HTTP APIs.

## Installation

Setup via `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "@VERSION@"
```

**NOTES ON VERSIONING:**

- Older library versions `1.x` or `2.x` with organization `io.d11` of ZIO HTTP are derived from Dream11, the organization that donated ZIO HTTP to the ZIO organization in 2022.
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
  val routes =
    Routes(
      Method.GET / Root -> handler(Response.text("Greetings at your service")),
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryParamToOrElse("name", "World")
        Response.text(s"Hello $name!")
      }
    )

  def run = Server.serve(routes).provide(Server.default)
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
