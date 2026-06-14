# Client

The ZIO HTTP Client allows your application to make outbound HTTP requests to external services. Built on ZIO and Netty, it provides a purely functional, type-safe API for HTTP communication with support for connection pooling, streaming, and middleware.

## Client as a Function

At its core, the client can be thought of as a function from `Request` to `Response`:

```
Request => ZIO[R, Throwable, Response]
```

You construct a request, send it through the client, and receive a response wrapped in a ZIO effect. The effect captures potential failures (network errors, timeouts, malformed responses) in a type-safe manner.

```scala mdoc:compile-only
import zio._
import zio.http._

val program =
  for {
    response <- Client.batched(Request.get("https://api.example.com/users"))
    body     <- response.body.asString
  } yield body
```

## Batched vs Streaming

The client supports two execution modes:

**Batched mode** buffers the entire response body in memory. Connection lifecycle is managed automatically. Use this for typical API calls where response sizes are reasonable.

**Streaming mode** allows processing large response bodies without loading them entirely into memory. You manage the connection scope explicitly, which is necessary when the response body is a stream. Use this for large file downloads or responses of unknown size.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.stream._

// Batched - automatic resource management
val batched = Client.batched(Request.get("https://api.example.com/data"))

// Streaming - explicit scope management  
val streaming = ZIO.scoped {
  Client.streaming(Request.get("https://example.com/large-file"))
    .flatMap(_.body.asStream.runCollect)
}
```

## Connection Pooling

The client maintains a pool of connections to hosts, reusing them across requests. This avoids the overhead of establishing new TCP connections for every request. The connection pool is configured automatically but can be tuned:

- Maximum connections per host
- Idle connection timeout
- Connection TTL

## SSL/TLS Support

The client supports secure connections out of the box. For most cases, the default SSL configuration works. Custom SSL configurations are available for scenarios like self-signed certificates or client certificate authentication.

## Middleware

Client middleware lets you intercept requests and responses for cross-cutting concerns:

- Logging outgoing requests
- Adding authentication headers
- Retrying failed requests
- Metrics and tracing

Middleware composes, so you can layer multiple concerns without tangling your business logic.

For detailed configuration and examples, see the [Client Reference](./../reference/client.md).
