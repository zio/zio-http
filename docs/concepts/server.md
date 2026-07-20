# Server

The ZIO HTTP Server is the component that listens for incoming HTTP connections, processes requests through your application's routes, and sends back responses. It handles all the low-level networking concerns so you can focus on your application logic.

## Server as a Resource

A ZIO HTTP server is modeled as a ZIO resource with proper lifecycle management. When you start a server, it acquires network resources (ports, threads, connection pools), and when the application terminates, these resources are properly released. This is handled through ZIO's built-in resource management.

```scala mdoc:compile-only
import zio._
import zio.http._

val routes = Routes(
  Method.GET / "hello" -> handler(Response.text("Hello!"))
)

// The server starts when the effect runs and stops when it completes
Server.serve(routes).provide(Server.default)
```

## Configuring the Server

The server supports extensive configuration for production deployments:

- **Binding address and port** - Where the server listens for connections
- **SSL/TLS** - Secure communication with clients
- **Request decompression** - Handle gzip/deflate compressed requests
- **Response compression** - Compress responses for bandwidth savings
- **Keep-alive** - Connection reuse settings
- **Timeouts** - Idle timeout and graceful shutdown duration

The default configuration works for development, but production deployments typically require tuning these settings.

```scala mdoc:compile-only
import zio._
import zio.http._

val prodConfig = Server.Config.default
  .port(443)
  .idleTimeout(60.seconds)
  .responseCompression()
```

## Installing vs Serving

ZIO HTTP provides two ways to run your routes:

- `Server.serve(routes)` - Starts the server and keeps it running until interrupted
- `Server.install(routes)` - Installs routes into the server but doesn't block, useful when you need to run other operations alongside the server

Most applications use `serve`, but `install` is helpful for scenarios like running background jobs while serving HTTP requests.

## Netty Backend

Under the hood, ZIO HTTP uses [Netty](https://netty.io/) for high-performance networking. Netty provides event-driven, non-blocking I/O that scales to thousands of concurrent connections. The server automatically configures Netty with sensible defaults, but advanced users can customize the Netty channel pipeline through the server configuration.

For detailed server configuration options and examples, see the [Server Reference](./../reference/server.md).
