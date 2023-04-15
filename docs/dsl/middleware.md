---
id: middleware
title: Middleware
---

Before introducing middleware, let us understand why they are needed.

Consider the following example where we have two endpoints within HttpApp
* GET a single user by id
* GET all users

```scala
private val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "users" / id =>
    // core business logic  
    dbService.lookupUsersById(id).map(Response.json(_.json))
  case Method.GET -> !! / "users"    =>
    // core business logic  
    dbService.paginatedUsers(pageNum).map(Response.json(_.json))
}
```

#### The polluted code violates the principle of "Separation of concerns"

As our application grows, we want to code the following aspects like
* Basic Auth
* Request logging
* Response logging
* Timeout and retry

For both of our example endpoints, our core business logic gets buried under boilerplate like this

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
Imagine repeating this for all our endpoints!!!

So there are two problems with this approach
* We are dangerously coupling our business logic with cross-cutting concerns (like applying timeouts)
* Also, addressing these concerns will require updating code for every single route in the system. For 100 routes we will need to repeat 100 timeouts!!!
* For example, any change related to a concern like the logging mechanism from logback to log4j2 may cause changing signature of `log(..)` function in 100 places.
* On the other hand, this also makes testing core business logic more cumbersome.


This can lead to a lot of boilerplate clogging our neatly written endpoints affecting readability, thereby leading to increased maintenance costs.

## Need for middlewares and handling "aspects"

If we refer to Wikipedia for the definition of an "[Aspect](https://en.wikipedia.org/wiki/Aspect_(computer_programming))" we can glean the following points.

* An aspect of a program is a feature linked to many other parts of the program (**_most common example, logging_**)., 
* But it is not related to the program's primary function (**_core business logic_**) 
* An aspect crosscuts the program's core concerns (**_for example logging code intertwined with core business logic_**),  
* Therefore, it can violate the principle of "separation of concerns" which tries to encapsulate unrelated functions. (**_Code duplication and maintenance nightmare_**)

Or in short, aspect is a common concern required throughout the application, and its implementation could lead to repeated boilerplate code and in violation of the principle of separation of concerns.

There is a paradigm in the programming world called [aspect-oriented programming](https://en.wikipedia.org/wiki/Aspect-oriented_programming) that aims for modular handling of these common concerns in an application. 

Some examples of common "aspects" required throughout the application
- logging,
- timeouts (preventing long-running code)
- retries (or handling flakiness for example while accessing third party APIs)
- authenticating a user before using the REST resource (basic, or custom ones like OAuth / single sign-on, etc).

This is where middleware comes to the rescue. 
Using middlewares we can compose out-of-the-box middlewares (or our custom middlewares) to address the above-mentioned concerns using ++ and @@ operators as shown below.

#### Cleaned up code using middleware to address cross-cutting concerns like auth, request/response logging, etc.
Observe, how we can address multiple cross-cutting concerns using neatly composed middlewares, in a single place.

```scala mdoc:silent
import zio._
import zio.http._

// compose basic auth, request/response logging, timeouts middlewares
val composedMiddlewares = RequestHandlerMiddlewares.basicAuth("user","pw") ++ 
        RequestHandlerMiddlewares.debug ++ 
        RequestHandlerMiddlewares.timeout(5.seconds) 
```

And then we can attach our composed bundle of middlewares to an Http using `@@`

```scala
val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "users" / id =>
    // core business logic  
    dbService.lookupUsersById(id).map(Response.json(_.json))
  case Method.GET -> !! / "users"    =>
    // core business logic  
    dbService.paginatedUsers(pageNum).map(Response.json(_.json))
} @@ composedMiddlewares // attach composedMiddlewares to the app using @@
```

Observe how we gained the following benefits by using middlewares
* **Readability**: de-cluttering business logic.
* **Modularity**: we can manage aspects independently without making changes in 100 places. For example, 
  * replacing the logging mechanism from logback to log4j2 will require a change in one place, the logging middleware.
  * replacing the authentication mechanism from OAuth to single sign-on will require changing the auth middleware
* **Testability**: we can test our aspects independently.

## Middleware in zio-http

A middleware helps in addressing common crosscutting concerns without duplicating boilerplate code.

#### Attaching middleware to Http

`@@` operator is used to attach a middleware to an Http. Example below shows a middleware attached to an HttpApp

```scala mdoc:silent
val app = Http.collect[Request] {
  case Method.GET -> !! / name => Response.text(s"Hello $name")
}
val appWithMiddleware = app @@ RequestHandlerMiddlewares.debug
```

Logically the code above translates to `Middleware.debug(app)`

#### A simple middleware example

Let us consider a simple example using out-of-the-box middleware called ```addHeader```
We will write a middleware that will attach a custom header to the response. 

We create a middleware that appends an additional header to the response indicating whether it is a Dev/Prod/Staging environment.

```scala mdoc:silent:reset
import zio._
import zio.http._

lazy val patchEnv = RequestHandlerMiddlewares.addHeader("X-Environment", "Dev")
```

A test `App` with attached middleware:

```scala mdoc:silent
val app = Http.collect[Request] {
  case Method.GET -> !! / name => Response.text(s"Hello $name")
}
val appWithMiddleware = app @@ patchEnv
```

Start the server:

```scala mdoc:silent
Server.serve(appWithMiddleware).provide(Server.default)
```

Fire a curl request, and we see an additional header added to the response indicating the "Dev" environment:

```
curl -i http://localhost:8080/Bob

HTTP/1.1 200 OK
content-type: text/plain
X-Environment: Dev
content-length: 12

Hello Bob
```

## Combining middlewares

Middlewares can be combined using several special operators like `++`, `<>` and `>>>`

### Using `++` combinator

`>>>` and `++` are aliases for `andThen`. It combines two middlewares.

For example, if we have three middlewares f1, f2, f3

f1 ++ f2 ++ f3 applies on an `http`, from left to right with f1 first followed by others, like this 
```scala
  f3(f2(f1(http)))
```
#### A simple example using `++` combinator

Start with imports:

```scala mdoc:silent:reset
import zio.http._
import zio.http.RequestHandlerMiddlewares.basicAuth
import zio._
```

A user app with single endpoint that welcomes a user:

```scala mdoc:silent
val userApp = Http.collect[Request] { case Method.GET -> !! / "user" / name / "greet" =>
  Response.text(s"Welcome to the ZIO party! ${name}")
}
```

A basicAuth middleware with hardcoded user pw and another patches response with environment value:

```scala mdoc:silent
val basicAuthMW = basicAuth("admin", "admin")
val patchEnv = RequestHandlerMiddlewares.addHeader("X-Environment", "Dev")
// apply combined middlewares to the userApp
val appWithMiddleware = userApp @@ (basicAuthMW ++ patchEnv)
```

Start the server:

```scala mdoc:silent
Server.serve(appWithMiddleware).provide(Server.default)
```

Fire a curl request with an incorrect user/password combination:

```
curl -i --user admin:wrong http://localhost:8080/user/admin/greet

HTTP/1.1 401 Unauthorized
www-authenticate: Basic
X-Environment: Dev
content-length: 0
```

We notice in the response that first basicAuth middleware responded `HTTP/1.1 401 Unauthorized` and then patch middleware attached a `X-Environment: Dev` header. 

## Conditional application of middlewares

- `when` applies middleware only if the condition function evaluates to true
-`whenZIO` applies middleware only if the condition function(with effect) evaluates


## A complete example of a middleware

<details>
<summary><b>Detailed example showing "debug" and "addHeader" middlewares</b></summary>

```scala mdoc:silent:reset
import zio.http._
import zio._

import java.io.IOException
import java.util.concurrent.TimeUnit

object Example extends ZIOAppDefault {
  val app: App[Any] =
    Http.collectZIO[Request] {
      // this will return result instantly
      case Method.GET -> !! / "text" => ZIO.succeed(Response.text("Hello World!"))
      // this will return result after 5 seconds, so with 3 seconds timeout it will fail
      case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5.seconds)
    }

  val middlewares =
    RequestHandlerMiddlewares.debug ++ // print debug info about request and response 
      RequestHandlerMiddlewares.addHeader("X-Environment", "Dev") // add static header   

  override def run =
    Server.serve(app @@ middlewares).provide(Server.default)
}
```
   
</details>   

### A few "Out of the box" middlewares
- [Basic Auth](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/middleware_basic_auth) 
- [CORS](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/middleware_cors)
- [CSRF](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/middleware_csrf)

