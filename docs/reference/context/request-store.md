# RequestStore

**RequestStore** is a fiber-local storage mechanism in ZIO HTTP that allows you to store and retrieve request-scoped data throughout the lifecycle of an HTTP request. It provides a type-safe way to share context across middleware, handlers, and service layers without explicit parameter passing.

## Overview

RequestStore uses ZIO's `[FiberRef](https://zio.dev/reference/state-management/fiberref/)` under the hood to ensure that data is isolated per request and automatically cleaned up when the request completes. This makes it ideal for storing contextual information that needs to be accessed at various points during request processing without leaking memory. Automatic cleanup is built-in, so there's no manual cleanup needed—data is cleared when the fiber completes.

RequestStore excels at managing request-scoped context throughout your application. One of the common use cases is request context tracking, where you store user IDs, session IDs, timestamps, IP addresses, correlation IDs, trace IDs, and other contextual information extracted from headers or authentication tokens, making this data available to all layers of your application without explicit parameter passing.

## API

The core API of RequestStore consists of three main functions:

```scala
object RequestStore {
  // Retrieve a value from the store
  def get[A: Tag]: UIO[Option[A]]
  
  // Store a value in the store
  def set[A: Tag](a: A): UIO[Unit]
  
  // Update a value in the store
  def update[A: Tag](f: Option[A] => A): UIO[Unit]
}
```

You can think of `RequestStore` as a type-safe, request-scoped key-value store where the keys are the types of the values you want to store.

The `Tag` context bound ensures type safety by requiring a type tag for the stored type, preventing accidental type mismatches.

## Basic Usage

Assume you have modeled some request-scoped data as `UserId`:

```scala mdoc:silent
case class UserId(value: String)
```

When writing authentication middleware, after validating the user, you can store the `UserId` in the `RequestStore`:

```scala mdoc:silent
import zio._
import zio.http._

def authorizeAndExtractUserId: Header.Authorization => Task[UserId] = ???

val authMiddleware: Middleware[Any] = new Middleware[Any] {
  override def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
    routes.transform { h =>
      Handler.scoped[Env1] {
        Handler.fromFunctionZIO { (req: Request) =>
          {
            for {
              header   <- ZIO.fromOption(req.header(Header.Authorization))
              userId   <- authorizeAndExtractUserId(header)
              _        <- RequestStore.set(userId)
              response <- h(req)
            } yield response
          } orElseFail Response.status(Status.Unauthorized)
        }
      }
    }
}
```

Whenever you need to access the `UserId` later in the request lifecycle, simply call `RequestStore.get`:


```scala mdoc:compile-only
import zio._
import zio.http._

def getProfile(str: UserId): Task[String] = ???

val routes = Routes(
  Method.GET / "profile" -> handler { (req: Request) =>
    for {
      userId  <- RequestStore.get[UserId].someOrFail(Response.notFound("No user id found"))
      profile <- getProfile(userId)
    } yield Response.text(profile)
  },
) @@ authMiddleware
```

You can also update existing data in the store using `RequestStore#update`.


## Integration with Other Features

RequestStore is used internally by `forwardHeaders` to store headers that should be forwarded to outgoing requests. For example, the following route forwards the `X-Request-Id` header to the downstream service when calling it:

```scala mdoc:compile-only
val routes = Routes(
  Method.GET / "users" -> handler { (req: Request) =>
    for {
      client   <- ZIO.service[Client]
      // Authorization header is automatically forwarded via RequestStore
      response <- (client @@ ZClientAspect.forwardHeaders)
        .batched(Request.get(url"http://user-service/users"))
    } yield response
  }
) @@ Middleware.forwardHeaders("X-Request-Id")
```
