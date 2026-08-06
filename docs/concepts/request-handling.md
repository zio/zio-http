# Request Handling

Understanding how requests flow through a ZIO HTTP application helps you structure your code and debug issues. This page explains the request lifecycle from arrival to response.

## The Request-Response Cycle

When an HTTP request arrives at your server:

1. The server receives the raw bytes from the network
2. ZIO HTTP parses the bytes into a `Request` object
3. The request is matched against your defined routes
4. If a route matches, the handler processes the request and produces a `Response`
5. If no route matches, a 404 response is generated
6. The response is serialized and sent back to the client

```
Client → Network → Server → Route Matching → Handler → Response → Network → Client
```

## Handlers

A `Handler` is a function that takes some input and produces a `Response`. The simplest handler ignores the request:

```scala mdoc:compile-only
import zio.http._

val simple = handler(Response.text("Hello"))
```

More typically, handlers inspect the request to produce a response:

```scala mdoc:compile-only
import zio.http._

val greeting = handler { (req: Request) =>
  val name = req.queryOrElse[String]("name", "World")
  Response.text(s"Hello, $name!")
}
```

Handlers can be effectful, performing database queries, calling external services, or any other ZIO operation:

```scala mdoc:compile-only
import zio._
import zio.http._

def getUser(id: String): ZIO[Any, Throwable, String] = ???

val userHandler = handler { (id: String, req: Request) =>
  getUser(id).map(user => Response.text(user))
}
```

## Error Handling

Errors in handlers are categorized as:

**Handled errors** - Errors that have been converted to HTTP responses. These are represented as `Response` values with appropriate status codes.

**Unhandled errors** - Errors that haven't been converted to responses yet. Before serving routes, you must handle all errors using methods like `handleError` or `handleErrorCause`.

```scala mdoc:compile-only
import zio._
import zio.http._

val routes = Routes(
  Method.GET / "might-fail" -> handler {
    ZIO.fail(new Exception("oops")).map(_ => Response.ok)
  }
).handleError { error =>
  Response.text(s"Error: ${error.getMessage}").status(Status.InternalServerError)
}
```

## Request Context

Handlers can access contextual information through:

- **Path parameters** - Values extracted from the URL path (e.g., `/users/:id`)
- **Query parameters** - Key-value pairs from the URL query string
- **Headers** - Request headers for content negotiation, authentication, etc.
- **Body** - The request body for POST/PUT/PATCH requests

ZIO HTTP provides typed access to all of these, catching parsing errors before your business logic runs.

## The Handler Environment

Handlers can require services from the ZIO environment, allowing dependency injection:

```scala mdoc:compile-only
import zio._
import zio.http._

trait UserService {
  def findUser(id: String): Task[Option[String]]
}

val userHandler = handler { (id: String, req: Request) =>
  ZIO.serviceWithZIO[UserService](_.findUser(id)).map {
    case Some(user) => Response.text(user)
    case None       => Response.notFound
  }
}
```

The required services are tracked in the route's type signature, ensuring all dependencies are provided before the server starts.

For more details on handlers, see the [Handler Reference](./../reference/handler.md).
