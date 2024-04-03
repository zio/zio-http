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

Consider the following example where we have two endpoints within HttpApp
* GET a single user by id
* GET all users

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
* Also, addressing these concerns will require updating the code for every single route in the system. For 100 routes we will need to repeat 100 timeouts!!!
* For example, any change related to a concern like the logging mechanism from logback to log4j2 may cause changing the signature of `log(..)` function in 100 places.
* On the other hand, this also makes testing core business logic more cumbersome.

This can lead to a lot of boilerplate clogging our neatly written endpoints affecting readability, thereby leading to increased maintenance costs.

### The solution: Middleware and Aspect-oriented Programming

If we refer to Wikipedia for the definition of an "[Aspect](https://en.wikipedia.org/wiki/Aspect_(computer_programming))" we can glean the following points.

* An aspect of a program is a feature linked to many other parts of the program (**_most common example, logging_**)., 
* But it is not related to the program's primary function (**_core business logic_**) 
* An aspect crosscuts the program's core concerns (**_for example logging code intertwined with core business logic_**),  
* Therefore, it can violate the principle of "separation of concerns" which tries to encapsulate unrelated functions. (**_Code duplication and maintenance nightmare_**)

In short, aspect is a common concern required throughout the application, and its implementation could lead to repeated boilerplate code and violation of the principle of separation of concerns.

There is a paradigm in the programming world called [aspect-oriented programming](https://en.wikipedia.org/wiki/Aspect-oriented_programming) that aims for modular handling of these common concerns in an application. 

Some examples of common "aspects" required throughout the application
- logging,
- timeouts (preventing long-running code)
- retries (or handling flakiness for example while accessing third-party APIs)
- authenticating a user before using the REST resource (basic, or custom ones like OAuth / single sign-on, etc).

This is where middleware comes to the rescue. 
Using middlewares we can compose out-of-the-box middlewares (or our custom middlewares) to address the above-mentioned concerns using ++ and @@ operators as shown below.

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

## Applying `Middleware` to `HttpApp`

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

## Conditional Application of Middlewares

- `when` applies middleware only if the condition function evaluates to true
  -`whenZIO` applies middleware only if the condition function(with effect) evaluates

## Built-in Middlewares

ZIO HTTP offers a versatile set of built-in middlewares, designed to enhance and customize the handling of HTTP requests and responses. These middlewares can be easily integrated into your application to provide various functionalities. Below is a comprehensive list of ZIO HTTP middlewares along with brief descriptions:

| Number | Description                                            |  Middleware                                                  |
|--------|--------------------------------------------------------|----------------------------------------------------|
| 1      | Cross-Origin Resource Sharing (CORS) Middleware        | `Middleware.cors`, `Middleware.corsHeaders`        |
| 2      | Log Annotations Middleware                             | `Middleware.logAnnotate`, `Middleware.logAnnotateHeaders`|
| 3      | Timeout Middleware                                     | `Middleware.timeout`                               |
| 4      | Metrics Middleware                                     | `Middleware.metrics`                               |
| 5      | Serving Static Files Middleware                        | `Middleware.serveResources`, `Middleware.serveDirectory`|
| 6      | Managing The Flash Scope                               | `Middleware.flashScopeHandling`                    |
| 7      | Basic Authentication                                   | `Middleware.basicAuth`, `Middleware.basicAuthZIO`  |
| 8      | Bearer Authentication                                  | `Middleware.bearerAuth`, `bearerAuthZIO`            |
| 9      | Custom Authentication                                  | `Middleware.customAuth`, `Middleware.customAuthZIO`, `Middleware.customAuthProviding`, `Middleware.customAuthProvidingZIO`|
| 10     | Beautify Error Response                                | `Middleware.beautifyErrors`                         |
| 11     | Debugging Requests and Responses                       | `Middleware.debug`                                 |
| 12     | Drop Trailing Slash                                    | `Middleware.dropTrailingSlash`                     |
| 13     | Aborting Requests with Specified Response              | `Middleware.fail`, `Middleware.failWith`            |
| 14     | Identity Middleware (No effect on request or response) | `Middleware.identity`                          |
| 15     | Conditional Middlewares                                | `Middleware.ifHeaderThenElse`, `Middleware.ifMethodThenElse`, `Middleware.ifRequestThenElse`, `Middleware.ifRequestThenElseZIO`, `Middleware.whenHeader`, `Middleware.whenResponse`, `Middleware.whenResponseZIO`, `Middleware.when`, `Middleware.whenZIO`|
| 16     | Intercept Middleware                                   | `Middleware.intercept`, `Middleware.interceptHandler`, `Middleware.interceptHandlerStateful`, `Middleware.interceptIncomingHandler`, `Middleware.interceptOutgoingHandler`, `Middleware.interceptPatch`, `Middleware.interceptPatchZIO`|
| 17     | Patch Middleware                                       | `Middleware.patch`, `Middleware.patchZIO`          |
| 18     | Redirect Middleware                                    | `Middleware.redirect`, `Middleware.redirectTrailingSlash`|
| 19     | Request Logging Middleware                             | `Middleware.requestLogging`                         |
| 20     | Running Effect Before/After Every Request              | `Middleware.runBefore`, `Middleware.runAfter`      |
| 21     | Add Cookie                                             | `Middleware.addCookie`, `Middleware.addCookieZIO`  |
| 22     | Sign Cookies                                           | `Middleware.signCookies`                           |
| 23     | Update Response Status                                 | `Middleware.status`                                |
| 24     | Update Response Headers                                | `Middleware.updateHeaders`                         |
| 25     | Update Request's Method                                | `Middleware.updateMethod`                          |
| 26     | Update Request's Path                                  | `Middleware.updatePath`                            |
| 27     | Update Request                                         | `Middleware.updateRequest`, `Middleware.updateRequestZIO`|
| 28     | Update Response                                        | `Middleware.updateResponse`, `Middleware.updateResponseZIO`|
| 29     | Update Request's URL                                   | `Middleware.updateURL`                             |
| 30     | Allow/Disallow Accessing to an HTTP                    | `Middleware.allow`                                 |

## Examples

### A simple middleware example

Let us consider a simple example using out-of-the-box middleware called ```addHeader```. We will write a middleware that will attach a custom header to the response.

We create a middleware that appends an additional header to the response indicating whether it is a Dev/Prod/Staging environment:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithMiddlewares.scala")
```

Fire a curl request, and we see an additional header added to the response indicating the "Dev" environment:

```bash
curl -i http://localhost:8080/Bob

HTTP/1.1 200 OK
content-type: text/plain
X-Environment: Dev
content-length: 12

Hello Bob
```

### CORS Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithCORS.scala")
```

### Bearer Authentication Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/AuthenticationServer.scala")
```

### Basic Authentication Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/BasicAuth.scala")
```

To the example, start the server and fire a curl request with an incorrect user/password combination:

```bash
curl -i --user admin:wrong http://localhost:8080/user/admin/greet

HTTP/1.1 401 Unauthorized
www-authenticate: Basic
content-length: 0
```

We notice in the response that first basicAuth middleware responded `HTTP/1.1 401 Unauthorized` and then patch middleware attached a `X-Environment: Dev` header. 

### Endpoint Middleware Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/EndpointExamples.scala")
```

### Serving Static Files Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StaticFiles.scala")
```
