---
id: zio-environment
title: "Request-scoped Context via ZIO Environment"
sidebar_label: "ZIO Environment"
---

ZIO HTTP provides request-scoped context through ZIO's Environment system, which offers type-safe dependency injection and context propagation. The primary mechanism is `[HandlerAspect](../aop/handler_aspect.md)` with output context (`CtxOut`), not a dedicated `[RequestStore](request-store.md)` API. This approach leverages ZIO's `R` type parameter to pass request-specific data through the middleware stack to handlers.

## Overview

Request-scoped context in ZIO HTTP refers to data tied to the lifetime of a single HTTP request that needs to be accessible throughout the request processing pipeline. Common use cases include authentication tokens, user sessions, correlation IDs, and request metadata. ZIO HTTP solves this through `[HandlerAspect](../aop/handler_aspect.md)`, a specialized middleware type that produces typed context values accessible via the ZIO environment. Middleware extracts relevant context from requests and passes it through the `CtxOut` type parameter, which handlers access via `ZIO.service[T]` or `withContext`.

The ZIO Environment approach differs fundamentally from the FiberRef-based pattern called [RequestStore](request-store.md). `HandlerAspect` provides compile-time type safety: the context requirement appears explicitly in handler type signatures, ensuring all dependencies are satisfied before the application compiles. This prevents entire classes of runtime errors where missing context would only be discovered during execution.

## HandlerAspect

`HandlerAspect` is ZIO HTTP's middleware abstraction that can produce typed context values. Its type signature reveals the key insight:

```scala
final case class HandlerAspect[-Env, +CtxOut](
  protocol: ProtocolStack[Env, Request, (Request, CtxOut), Response, Response]
) extends Middleware[Env]
```

**The CtxOut type parameter** represents the context produced by middleware. When middleware processes a request, it returns a tuple `(Request, CtxOut)` where CtxOut contains the extracted context. This context then flows through the middleware stack and becomes available to handlers via ZIO's service pattern.

When you compose multiple `HandlerAspects`, their contexts combine as tuples: `HandlerAspect[Env, A] ++ HandlerAspect[Env, B]` produces `HandlerAspect[Env, (A, B)]`. This compositional approach allows building complex context from simple middleware components.

Please note that the ZIO Environment (the R in `ZIO[R, E, A]`) tracks dependencies at the type level. Every effect declares what **services** or **contexts** it requires to execute. ZIO HTTP extends this pattern through `HandlerAspect`, which provides a **bridge between HTTP middleware and the ZIO Environment system**.


## Generating Context in HandlerAspect

The `HandlerAspect.interceptIncomingHandler` API creates middleware that processes incoming requests and produces a context. The handler receives the Request and must return `(Request, CtxOut)` or fail with a Response:

```scala
def interceptIncomingHandler[Env, CtxOut](
  handler: Handler[Env, Response, Request, (Request, CtxOut)]
): HandlerAspect[Env, CtxOut]
```

For example, the following middleware extracts an Authorization header, authenticates the user, and produces a `User` context. If authentication fails, it returns a 401 Unauthorized response:

```scala mdoc:invisible
case class User(name: String)
trait UserService
```

```scala mdoc:silent
import zio._
import zio.http._

def authenticate(header: Header.Authorization): ZIO[UserService, Throwable, User] = ???

val auth: HandlerAspect[UserService, User] =
  HandlerAspect.interceptIncomingHandler {
    Handler.fromFunctionZIO[Request] { request =>
      ZIO
        .fromOption(request.headers.get(Header.Authorization))
        .orElseFail(Response.unauthorized("No Authorization header"))
        .flatMap(authenticate)
        .map(user => (request, user))
        .orElseFail(Response.unauthorized("Invalid token"))
    }
  }
```

This middleware has a type of `HandlerAspect[UserService, User]`, meaning it requires a `UserService` in the environment to perform authentication and produces a `User` context for downstream handlers.

## Accessing Context in Handlers

Using `ZIO.service` and its variants, handlers can access the context produced by middleware. The important note here is that `ZIO.service` can be used to access both the ZIO environment and the context produced by `HandlerAspect`:

```scala mdoc:compile-only
val greetRoute: Route[UserService, Nothing] = 
  Method.GET / "greet" -> handler { (_: Request) =>
    ZIO.serviceWith[User] { user =>
      Response.text(s"Hello, $user!")
    }
  } @@ auth
```

This handler is of type `Handler[User & UserService, Nothing, Request, Response]`, meaning it requires a `User` and `UserService` in the environment. Let's take a closer look at the type signature of `Handler`:

```scala
Handler[-R, +Err, -In, +Out]
// R: Environment/context required
// Err: Error type
// In: Input type (typically Request)
// Out: Output type (typically Response)
```

The first type parameter `R` represents the environment or context required by the handler. This can be either a service that can be provided via ZLayer or a context produced by `HandlerAspect`. In this example, the `User` is a request-scoped context produced by the `auth` middleware, while `UserService` is a service that can be provided via ZLayer in upper layers. Therefore, the handler requires both `User` and `UserService` in its environment.

Since the handler is wrapped with the `auth` middleware, it can access the `User` context produced by the `auth` middleware, which has a type of `HandlerAspect[UserService, User]`. By applying the middleware to the handler using the `@@` operator, the `User` context is provided to the handler, and so the handler type becomes `Handler[UserService, Nothing, Request, Response]`, meaning it only requires `UserService` from the environment.

Instead of `ZIO.service`, we can use the helper method `withContext` to access the context:

```scala mdoc:compile-only
val greetRoute: Route[UserService, Nothing] =
  Method.GET / "greet" -> handler { (_: Request) =>
    withContext { (user: User) =>
      Response.text(s"Hello, $user!")
    }
  } @@ auth
```

## Request Context Alongside Environmental Services Inside Handler

A curious reader might wonder what happens if the handler also requires a service from ZLayer. How can we combine both request context and application services in the same handler? The answer is that we treat them the same way - both are part of the ZIO environment, but the difference is when and who provides them. So in previous examples, the `User` context is provided by the `auth` middleware, while the `UserService` will be provided later. The same applies to any other service that the handler might require.

Let's see what happens when, other than the `auth` middleware, the handler also requires a service from the environment. For example, assume we have a `GreetingService` that generates personalized greetings based on the user information and the current time of day:

```scala mdoc:invisible
trait GreetingService {
  def greet(user: User): UIO[String]
}
```

Now we have to use the environment for `User`, `UserService`, and `GreetingService`. The `User` is a context produced by the `auth` middleware, while the `UserService` and `GreetingService` are services provided via ZLayer in upper layers. The handler can access both the `User` context and the `GreetingService` service using `ZIO.service`:

```scala mdoc:compile-only
val greetRoute: Route[UserService & GreetingService, Nothing] =
  Method.GET / "greet" ->
    handler(ZIO.service[GreetingService]).flatMap { greetingService =>
      handler {
        ZIO.serviceWithZIO[User] { user =>
          greetingService.greet(user).map(Response.text(_))
        }
      } @@ auth
    }
```

In this example, the handler has a type of `Handler[UserService & GreetingService, Response, Request, Response]`, meaning it requires both `UserService` and `GreetingService` from the environment. The `User` context is already provided by the `auth` middleware, while the provision of `UserService` and `GreetingService` is deferred to upper layers when serving the application.

Again, we can simplify the handler using `withContext`:

```scala mdoc:compile-only
val greetRoute: Route[UserService & GreetingService, Nothing] =
  Method.GET / "greet" ->
    handler(ZIO.service[GreetingService]).flatMap { greetingService =>
      handler {
        withContext { (user: User) =>
          greetingService.greet(user).map(Response.text(_))
        }
      } @@ auth
    }
```

## Composing Multiple Contexts

When multiple middleware components provide context, their contexts compose as tuples:

```scala mdoc:invisible
case class MetricsContext()
```

```scala mdoc:compile-only
val authAspect: HandlerAspect[Any, User]              = ???
val requestIdAspect: HandlerAspect[Any, String]       = ???
val metricsAspect: HandlerAspect[Any, MetricsContext] = ???

// Composed aspect has tuple type
val composedAspect: HandlerAspect[Any, (User, String, MetricsContext)] =
  authAspect ++ requestIdAspect ++ metricsAspect

// Handler receives all contexts
val myHandler: Handler[(User, String, MetricsContext), Nothing, Request, Response] =
  handler { (_: Request) =>
    ZIO.service[(User, String, MetricsContext)].map { case (user, requestId, metrics) =>
      Response.text(s"User: ${user.name}, RequestID: $requestId, metrics: $metrics")
    }
  }

val exampleRoute = 
  Method.GET / "example" -> myHandler @@ composedAspect
```

Also, we can use `withContext`:

```scala mdoc:compile-only
val myHandler: Handler[User & String & MetricsContext, Nothing, Request, Response] =
  handler { (_: Request) =>
    withContext { (user: User, requestId: String, metrics: MetricsContext) =>
      Response.text(s"User: ${user.name}, RequestID: $requestId, metrics: $metrics")
    }
  }
```
