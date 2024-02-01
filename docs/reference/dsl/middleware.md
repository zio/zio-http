---
id: middleware
title: Middleware
---

A middleware helps in addressing common crosscutting concerns without duplicating boilerplate code.

## Applying `Middleware` to `HttpApp`

The `@@` operator is used to attach a middleware to routes and HTTP applications. Example below shows a middleware attached to an `HttpApp`:

```scala mdoc:silent
import zio.http._

val app = Routes(
  Method.GET / string("name") -> handler { (name: String, req: Request) => 
    Response.text(s"Hello $name")
  }
).toHttpApp
val appWithMiddleware = app @@ Middleware.debug
```

Logically the code above translates to `Middleware.debug(app)`, which transforms the app using the middleware.

## Combining Middlewares

Middlewares can be combined using the `++` operator.

For example, if we have three middlewares f1, f2, f3, the `f1 ++ f2 ++ f3` applies from left to right with `f1` first followed by others, like this:

```scala
f3(f2(f1(http)))
```

## Conditional Middleware Application

- `when` applies middleware only if the condition function evaluates to true
- `whenZIO` applies middleware only if the condition function(with effect) evaluates

## Example

Detailed example showing "debug" and "addHeader" middlewares

```scala mdoc:silent:reset
import zio.http._
import zio._

import java.io.IOException
import java.util.concurrent.TimeUnit

object Example extends ZIOAppDefault {
  val app: HttpApp[Any] =
    Routes(
      // this will return result instantly
      Method.GET / "text" -> handler(Response.text("Hello World!")),
      // this will return result after 5 seconds, so with 3 seconds timeout it will fail
      Method.GET / "long-running" -> handler(ZIO.succeed(Response.text("Hello World!")).delay(5.seconds))
    ).toHttpApp

  val middlewares =
    Middleware.debug ++ // print debug info about request and response 
      Middleware.addHeader("X-Environment", "Dev") // add static header   

  override def run =
    Server.serve(app @@ middlewares).provide(Server.default)
}
```

## Built-in Middlewares

ZIO HTTP provides a set of built-in middlewares that can be used out of the box. To learm more please refer to the following examples:

- [Basic Auth](../../examples/authentication.md#basic-authentication-middleware-example) 
- [CORS](../../examples/middleware-cors-handling.md)

