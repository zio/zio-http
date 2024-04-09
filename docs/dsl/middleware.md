---
id: middleware
title: Middleware
---

A middleware helps in addressing common crosscutting concerns without duplicating boilerplate code.

## Definition

Middleware can be conceptualized as a functional component that accepts a `Routes` and produces a new `Routes`. The defined trait, `Middleware`, is parameterized by a contravariant type `UpperEnv` which denotes it can access the environment of the `HttpApp`:

```scala
trait Middleware[-UpperEnv] { self =>
  def apply[Env1 <: UpperEnv, Err](routes: Routes[Env1, Err]): Routes[Env1, Err]
} 
```

This abstraction allows middleware to engage with the `HttpApp` environment, and also the ability to tweak existing routes or add/remove routes as needed.

## Motivation

Before introducing middleware, let us understand why they are needed.

### The Problem: Violation of Separation of Concerns Principle

Consider the following example where we have two endpoints:

* **GET /users/{id}** - Get a single user by id
* **GET /users** - Get all users

```scala
val routes = Routes(
  Method.GET / "users" / int("id") -> 
    handler { (id: Int, req: Request) =>
      // core business logic  
      dbService.lookupUsersById(id).map(Response.json(_.json))
    },
  Method.GET / "users" ->
    handler {
      // core business logic  
      dbService.paginatedUsers(pageNum).map(Response.json(_.json))
    }
)
```

As our application grows, we want to code the aspects like Basic Authentication, Request Logging, Response Logging, Timeout, and Retry for all our endpoints.

For both of our example endpoints, our core business logic gets buried under boilerplate like this:

```scala
(for {
    // validate user
    _    <- MyAuthService.doAuth(request)
    // log request
    _    <- logRequest(request)
    // core business logic
    user <- dbService.lookupUsersById(id).map(Response.json(_.json))
    resp <- Response.json(user.toJson)
    // log response
    _    <- logResponse(resp)                
} yield resp)
        .timeout(2.seconds)
        .retryN(5)
```

Imagine repeating this for all our endpoints!

So there are some problems with this approach:

* **Violation of Separation of Concerns Principle**: Our current approach conflates business logic with cross-cutting concerns, such as timeouts, which violates the Separation of Concerns Principle. This coupling complicates the maintenance and understanding of our codebase.
* **Code Duplication**: Replicating cross-cutting concerns across multiple routes results in unnecessary code duplication. For instance, if there are 100 routes, each requiring a timeout, we're forced to repeat the same logic 100 times. Consequently, any modification or upgrade to a shared concern, like altering the logging mechanism, necessitates making changes in numerous locations, significantly increasing the risk of errors and maintenance effort.
* **Maintenance Nightmare**: With this approach, even a minor alteration in a cross-cutting concern demands updating every corresponding route. This not only escalates maintenance efforts but also complicates testing and debugging of core business logic. Consequently, the overall maintenance cost and complexity of the system are amplified.
* **Readability Issues**— This can lead to a lot of boilerplate clogging our neatly written endpoints affecting readability.

### The solution: Middleware and Aspect-oriented Programming

If we refer to Wikipedia for the definition of an "[Aspect](https://en.wikipedia.org/wiki/Aspect_(computer_programming))" we can glean the following points.

* An aspect of a program is a feature linked to many other parts of the program (**_most common example, logging_**).
* Tt is not related to the program's primary function (**_core business logic_**).
* An aspect crosscuts the program's core concerns (**_for example logging code intertwined with core business logic_**).
* Therefore, it can violate the principle of "separation of concerns" which tries to encapsulate unrelated functions. (**_Code duplication and maintenance nightmare_**)

In short, aspect is a common concern required throughout the application, and its implementation could lead to repeated boilerplate code and violation of the principle of separation of concerns.

There is a paradigm in the programming world called [aspect-oriented programming](https://en.wikipedia.org/wiki/Aspect-oriented_programming) that aims for modular handling of these common concerns in an application.

Some examples of common "aspects" required throughout the application

* **Logging**— Essential for tracking system behavior and troubleshooting
* **Timeouts**— Used for preventing long-running code.
* **Retries**— Vital for handling flakiness, particularly when accessing third-party APIs.
* **Authentication**— Ensure users are authenticated before accessing REST resources, utilizing standard methods like basic authentication or more advanced approaches such as OAuth or single sign-on.

This is where `Middleware` comes to the rescue. Using middlewares we can compose out-of-the-box middlewares (or our custom middlewares) to address the above-mentioned concerns using `++` and `@@` operators as shown below.

### The Solution: Middleware in ZIO-HTTP

We cleaned up the code using middleware to address cross-cutting concerns such as authentication, request/response logging, and more. See how we can handle multiple cross-cutting concerns by neatly composing middlewares in a single place:

```scala mdoc:silent
import zio._
import zio.http._

// compose basic auth, request/response logging, timeouts middlewares
val composedMiddlewares = Middleware.basicAuth("user","pw") ++ 
        Middleware.debug ++ 
        Middleware.timeout(5.seconds) 
```

And then we can attach our composed bundle of middlewares to an Http using `@@`

```scala
 val routes = Routes(
  Method.GET / "users" / int("id") -> 
    handler { (id: Int, req: Request) =>
      // core business logic  
      dbService.lookupUsersById(id).map(Response.json(_.json))
    },
  Method.GET / "users" ->
    handler {
      // core business logic  
      dbService.paginatedUsers(pageNum).map(Response.json(_.json))
    }
) @@ composedMiddlewares // attach composedMiddlewares to the routes using @@
```

Observe how we gained the following benefits by using middlewares

* **Readability**: de-cluttering business logic.
* **Modularity**: we can manage aspects independently without making changes in 100 places. For example, 
  * replacing the logging mechanism from logback to log4j2 will require a change in one place, the logging middleware.
  * replacing the authentication mechanism from OAuth to single sign-on will require changing the auth middleware
* **Testability**: we can test our aspects independently.

## Building a Custom Middleware

In most cases, we won't need a custom middleware. Instead, we have plenty of built-in middlewares that are ready to use. However, if we have a specific use case, we can create a custom middleware.

To build a custom middleware, we have to implement the `Middleware` trait, which requires a single method `apply` to be implemented. The `apply` method accepts a `Routes` and returns a new `Routes`.

For example, assume we want to replace every request path that starts with `/api` with `/v1/api`. We can create a custom middleware to achieve this:

```scala mdoc:compile-only
val urlRewrite: Middleware[Any] =
  new Middleware[Any] {
    override def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
      routes.transform { handler =>
        Handler.fromFunctionZIO { request =>
          handler(
            request.updateURL(url =>
              if (url.path.startsWith(Path("/api")))
                url.copy(path = Path("/v1") ++ url.path)
              else url,
            ),
          )
        }
      }
  }
```

The above implementation is just for demonstration purposes. In practice, we can use the built-in `Middleware.updatePath` to achieve the same functionality:

```scala mdoc:compile-only
val urlRewrite: Middleware[Any] =
  Middleware.updateURL(url =>
    if (url.path.startsWith(Path("/api")))
      url.copy(path = Path("/v1") ++ url.path)
    else url,
  )
```

## Built-in Middlewares

Inside the companion object `Middleware`, we have several built-in middlewares that are ready to use. Here is a list of them:

| Number | Description                                     | Middleware                                                |
|--------|-------------------------------------------------|-----------------------------------------------------------|
| 1      | Cross-Origin Resource Sharing (CORS) Middleware | `Middleware.cors`                                         |
| 2      | Log Annotations Middleware                      | `Middleware.logAnnotate`, `Middleware.logAnnotateHeaders` |
| 3      | Timeout Middleware                              | `Middleware.timeout`                                      |
| 4      | Metrics Middleware                              | `Middleware.metrics`                                      |
| 5      | Serving Static Files Middleware                 | `Middleware.serveResources`, `Middleware.serveDirectory`  |

The `Middleware` object also inherits many other middlewares from the `HandlerAspect`, that we will introduce them on the [HandlerAspect](./handler_aspect.md) page. 

## Attaching `Middleware` to `HttpApp`

The `@@` operator is used to attach middleware to routes and HTTP applications. The example below shows a middleware attached to an `HttpApp`:

```scala mdoc:compile-only
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

Middleware can be combined using the `++` operator.

For example, if we have three middlewares f1, f2, f3, the `f1 ++ f2 ++ f3` applies from left to right with `f1` first followed by others, like this:

```scala
f3(f2(f1(http)))
```
