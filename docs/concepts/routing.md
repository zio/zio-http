---
id: routing
title: "Routing"
---

# Routing

HTTP routing is a fundamental concept in ZIO HTTP. It allows developers to define how incoming requests are matched to specific handlers for processing and generating responses. ZIO HTTP facilitates routing through the `Http.collect` method, which accepts requests and produces corresponding responses. Pattern matching on routes is built-in, enabling developers to create precise routing logic.

## Basic Routing

To define routes in ZIO HTTP, you can use the `Http.collect` method, which accepts a partial function from requests to responses. Here's an example demonstrating how to create routes for a "Hello, World!" application:


```scala
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> Root / "hello" => Response.text("Hello, World!")
}
```

In this example, we define a simple HTTP route that responds with "Hello, World!" when a GET request is received for the `/hello` endpoint.

##Typed Routes

ZIO HTTP offers a type-safe approach to routing, promoting  robust and maintainable applications.

```scala
val app = Http.collect[Request] {
  case Method.GET -> !! / "Apple" / int(count)  => Response.text(s"Apple: $count")
}
```

This function uses pattern matching on incoming requests to determine how each request should be handled and responded to.

## Path Matching

Routes in ZIO HTTP can be defined to match specific paths and HTTP methods, allowing to create precise routing logic tailored to the application's needs. Path segments can be specified using literals or extracted as parameters for dynamic routing.

```scala
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> Root / "count" / int(count) => Response.text(s"Count: $count")
}
```

In this example, the route expects an integer value for the `count` path segment. When a request is made to `/count/123`, the response will contain the count value.

## Combining Routes

ZIO HTTP allows you to combine multiple routes using the `++` operator. This enables you to build complex routing structures for your application.

```scala
import zio.http._

val helloWorldRoute = Http.get("/hello") { ZIO.succeed(Response.text("Hello, World!")) }
val countRoute = Http.get("/count" / int(count)) { ZIO.succeed(Response.text(s"Count: $count")) }

val app = helloWorldRoute ++ countRoute
```
The code defines two HTTP routes using ZIO HTTP: one for responding `Hello, World!` on a GET request to `/hello`, and another for returning a count value on a GET request to `/count/{count}`. These routes are combined into an application (app) to handle HTTP requests efficiently.
