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

## Applying `Middleware` to `HttpApp`

The `@@` operator is used to attach a middleware to routes and HTTP applications. Example below shows a middleware attached to an `HttpApp`:

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

Middlewares can be combined using the `++` operator.

For example, if we have three middlewares f1, f2, f3, the `f1 ++ f2 ++ f3` applies from left to right with `f1` first followed by others, like this:

```scala
f3(f2(f1(http)))
```

## Conditional Middleware Application

- `when` applies middleware only if the condition function evaluates to true
- `whenZIO` applies middleware only if the condition function(with effect) evaluates

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

### Hello World Example

Detailed example showing "debug" and "addHeader" middlewares

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithMiddlewares.scala")
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
