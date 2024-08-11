---
id: client
title: Client
---

`ZClient` is an HTTP client that enables us to make HTTP requests and handle responses in a purely functional manner. ZClient leverages the ZIO library's capabilities to provide a high-performance, asynchronous, and type-safe HTTP client solution.

## Key Features

**Purely Functional**: ZClient is built on top of the ZIO library, enabling a purely functional approach to handling HTTP requests and responses. This ensures referential transparency and composability, making it easy to build and reason about complex HTTP interactions.

**Type-Safe**: ZClient's API is designed to be type-safe, leveraging Scala's type system to catch errors at compile time and provide a seamless development experience. This helps prevent common runtime errors and enables developers to write robust and reliable HTTP client code.

**Asynchronous & Non-blocking**: ZClient is fully asynchronous and non-blocking, allowing us to perform multiple HTTP requests concurrently without blocking threads. This ensures optimal resource utilization and scalability, making it suitable for high-performance applications.

**Middleware Support**: ZClient provides support for middleware, allowing us to customize and extend its behavior to suit our specific requirements. We can easily plug in middleware to add functionalities such as logging, debugging, caching, and more.

**Flexible Configuration**: ZClient offers flexible configuration options, allowing us to fine-tune its behavior according to our needs. We can configure settings such as SSL, proxy, connection pooling, timeouts, and more to optimize the client's performance and behavior.

**WebSocket Support**: In addition to traditional HTTP requests, ZClient also supports WebSocket communication, enabling bidirectional, full-duplex communication between client and server over a single, long-lived connection.

**SSL Support**: ZClient provides built-in support for SSL (Secure Sockets Layer) connections, allowing secure communication over the network. Users can configure SSL settings such as certificates, trust stores, and encryption protocols to ensure data confidentiality and integrity.

## Making HTTP Requests

We can think of a `ZClient` as a function that takes a `Request` and returns a `ZIO` effect that calls the server with the given request and returns the response that the server sends back.

```scala
object Client {
  def request(request: Request): ZIO[Client & Scope, Throwable, Response] = ???
}
```

The `Client` and `Scope` environments are required to perform the request and handle the response. For the `Client` environment, we can use the default client provided by ZIO HTTP (i.e., `Client.default`). The `Scope` environment is used to manage the lifecycle of resources such as connections, sockets, and other I/O-related resources that are acquired and released during the request-response operation.

:::note
The `Scope` returned by this method is responsible for running finalizers associated with an HTTP request. It must be closed (using `ZIO.scoped`) after the body of a request has been collected.

To learn more about resource management and `Scope` in ZIO, refer to the [dedicated guide on this topic](https://zio.dev/reference/resource/scope) in the ZIO Core documentation.
:::

### "Simple" API

The handling of `Scope` can quickly become cumbersome in cases where we simply want to execute an HTTP request and not handle the lifetime of the HTTP request.
For this reason, the Client exposes a `simple` method and API where the HTTP request methods don't have Scope in the environment

```scala
object Client {
  def simple(request: Request): RIO[Client, Response]
}
```

::: warning
The `simple` API methods will materialize the entire body of the request to memory.
For extracting the body as a stream, use `stream` or use `Client.request` and manage the `Scope` manually instead
:::

Let's try to make a simple GET HTTP request using `Client`:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.schema.DeriveSchema
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

case class Todo(
  userId: Int,
  id: Int,
  title: String,
  completed: Boolean,
)

object Todo {
  implicit val todoSchema = DeriveSchema.gen[Todo]
}

object ClientExample extends ZIOAppDefault {
  val program: ZIO[Client, Throwable, Unit] = 
    for {
      res   <- Client.simple(Request.get("http://jsonplaceholder.typicode.com/todos"))
      todos <- res.body.to[List[Todo]]
      _     <- Console.printLine(s"The first task is '${todos.head.title}'")
    } yield ()

  override val run = program.provide(Client.default)
}
```

Another way to have a client is to obtain it from the environment manually using the `ZIO.service` method, then use it to make a request. Like the previous example, we have to provide the `Client` and `Scope` environments to the program:

```scala mdoc:invisible
case class Todo(userId: Int, id: Int, title: String, completed: Boolean)

object Todo {
  implicit val todoSchema = ???
}
```

```scala mdoc:compile-only
import zio._
import zio.http._

object ClientExample extends ZIOAppDefault {
  val program: ZIO[Client, Throwable, Unit] = 
    for {
      res   <- Client.simple(Request.get("http://jsonplaceholder.typicode.com/todos"))
      todos <- res.body.to[List[Todo]]
      _     <- Console.printLine(s"The first task is '${todos.head.title}'")
    } yield ()

  override val run = program.provide(Client.default)
}
```

ZIO HTTP has several utility methods to create different types of requests, such as `Client#get`, `Client#post`, `Client#put`, `Client#delete`, etc:

| Method                               | Description                                                           |
|--------------------------------------|-----------------------------------------------------------------------|
| `def get(suffix: String)`            | Performs a GET request with the given path suffix.                    |
| `def head(suffix: String)`           | Performs a HEAD request with the given path suffix.                   |
| `def patch(suffix: String)`          | Performs a PATCH request with the given path suffix.                  |
| `def post(suffix: String)(body: In)` | Performs a POST request with the given path suffix and provided body. |
| `def put(suffix: String)(body: In)`  | Performs a PUT request with the given path suffix and provided body.  |
| `def delete(suffix: String)`         | Performs a DELETE request with the given path suffix.                 |

## Performing WebSocket Connections

We can also think of a client as a function that takes a `WebSocketApp` and returns a `ZIO` effect that performs the WebSocket operations and returns a response:

```scala
object ZClient {
  def socket[R](socketApp: WebSocketApp[R]): ZIO[R with Client & Scope, Throwable, Response] = ???
}
```

Here is a simple example of how to use the `ZClient#socket` method to perform a WebSocket connection:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

object WebSocketSimpleClient extends ZIOAppDefault {

  val url = "ws://ws.vi-server.org/mirror"

  val socketApp: WebSocketApp[Any] =
    Handler

      // Listen for all websocket channel events
      .webSocket { channel =>
        channel.receiveAll {

          // Send a "foo" message to the server once the connection is established
          case UserEventTriggered(UserEvent.HandshakeComplete) =>
            channel.send(Read(WebSocketFrame.text("foo"))) *>
              ZIO.debug("Connection established and the foo message sent to the server")

          // Send a "bar" if the server sends a "foo"
          case Read(WebSocketFrame.Text("foo")) =>
            channel.send(Read(WebSocketFrame.text("bar"))) *>
              ZIO.debug("Received the foo message from the server and the bar message sent to the server")

          // Close the connection if the server sends a "bar"
          case Read(WebSocketFrame.Text("bar")) =>
            ZIO.debug("Received the bar message from the server and Goodbye!") *>
              channel.send(Read(WebSocketFrame.close(1000)))

          case _ =>
            ZIO.unit
        }
      }

  val app: ZIO[Client, Throwable, Unit] =
    for {
      url    <- ZIO.fromEither(URL.decode("ws://ws.vi-server.org/mirror"))
      client <- ZIO.serviceWith[Client](_.url(url))
      _      <- ZIO.scoped(client.socket(socketApp) *> ZIO.never)
    } yield ()

  val run: ZIO[Any, Throwable, Any] =
    app.provide(Client.default)

}
```

In the above example, we defined a WebSocket client that connects to a mirror server and sends and receives messages. When the connection is established, it receives the `UserEvent.HandshakeComplete` event and then it sends a "foo" message to the server. Consequently, the server sends a "foo" message, and the client responds with a "bar" message. Finally, the server sends a "bar" message, and the client closes the connection.

## Configuring Headers

By default, the client adds the `User-Agent` header to all requests. Additionally, as the `ZClient` extends the `HeaderOps` trait, we have access to all operations that can be performed on headers inside the client.

For example, to add a custom header we can use the `Client#addHeader` method:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.Header.Authorization

val program = for {
  client <- ZIO.serviceWith[Client](_.addHeader(Authorization.Bearer(token = "dummyBearerToken")))
  res    <- client.request(Request.get("http://localhost:8080/users"))
} yield ()
```

:::note
To learn more about headers and how they work, check out our dedicated section called [Header Operations](headers/headers.md#headers-operations) on the headers page.
:::

## Composable URLs

In ZIO HTTP, URLs are composable. This means that if we have two URLs, we can combine them to create a new URL. This is useful when we want to prevent duplication of the base URL in our code. For example, assume we have a base URL `http://localhost:8080` and we want to make several requests to different endpoints and query parameters under this base URL. We can configure the client with this URL using the `Client#url` and then every request will be made can be relative to this base URL:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.schema.DeriveSchema
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

case class User(name: String, age: Int)
object User {
  implicit val schema = DeriveSchema.gen[User]
}

val program: ZIO[Scope & Client, Throwable, Unit] =
  for {
    url    <- ZIO.fromEither(URL.decode("http://localhost:8080"))
    client <- ZIO.serviceWith[Client](_.url(url))
    _      <- ZIO.scoped(client.post("/users")(Body.from(User("John", 42))))
    res    <- ZIO.scoped(client.get("/users"))
    _      <- ZIO.scoped(client.delete("/users/1"))
    _      <- res.body.asString.debug
  } yield ()
```

The following methods are available for setting the base URL:

| Method Signature                | Description                                    |
|---------------------------------|------------------------------------------------|
| `Client#url(url: URL)`          | Sets the URL directly.                         |
| `Client#uri(uri: URI)`          | Sets the URL from the provided URI.            |
| `Client#path(path: String)`     | Sets the path of the URL from a string.        |
| `Client#path(path: Path)`       | Sets the path of the URL from a `Path` object. |
| `Client#port(port: Int)`        | Sets the port of the URL.                      |
| `Client#scheme(scheme: Scheme)` | Sets the scheme (protocol) for the URL.        |

The `Scheme` is a sealed trait that represents the different schemes (protocols) that can be used in a request. The available schemes are `HTTP` and `HTTPS` for HTTP requests, and `WS` and `WSS` for WebSockets.

Here is the list of methods that are available for adding URL, Path, and QueryParams to the client's configuration:

| Methods                                            | Description                                                            |
|----------------------------------------------------|------------------------------------------------------------------------|
| `Client#addUrl(url: URL)`                          | Adds another URL to the existing one.                                  |
| `Client#addPath(path: String)`                     | Adds a path segment to the URL.                                        |
| `Client#addPath(path: Path)`                       | Adds a path segment from a `Path` object to the URL.                   |
| `Client#addLeadingSlash`                           | Adds a leading slash to the URL path.                                  |
| `Client#addTrailingSlash`                          | Adds a trailing slash to the URL path.                                 |
| `Client#addQueryParam(key: String, value: String)` | Adds a query parameter with the specified key-value pair to the URL.   |
| `Client#addQueryParams(params: QueryParams)`       | Adds multiple query parameters to the URL from a `QueryParams` object. |

## Client Aspects/Middlewares

Client aspects are a powerful feature of ZIO HTTP, enabling us to intercept, modify, and extend client behavior. The `ZClientAspect` is represented as a function that takes a `ZClient` and returns a new `ZClient` with customized behavior. We apply aspects to a client using the `ZClient#@@` method, allowing modification of various execution aspects such as metrics, tracing, encoding, decoding, and debugging.

### Debugging Aspects

To debug the client, we can use the `ZClientAspect.debug` aspect, which logs the request details to the console. This is useful for debugging and troubleshooting client interactions, as it provides visibility into the low-level details of the HTTP requests and responses:

```scala mdoc:compile-only
import zio._
import zio.http._

object ClientWithDebugAspect extends ZIOAppDefault {
  val program =
    for {
      client <- ZIO.service[Client].map(_ @@ ZClientAspect.debug)
      _      <- client.simple(Request.get("http://jsonplaceholder.typicode.com/todos"))
    } yield ()

  override val run = program.provide(Client.default)
}
```

The `ZClientAspect.debug` also takes a partial function from `Response` to `String`, which enables us to customize the logging output based on the response. This is useful for logging specific details from the response, such as status code, headers, and body:

```scala
val debugResponse = ZClientAspect.debug { case res: Response => res.headers.mkString("\n") }

val program =
  for {
    client <- ZIO.service[Client].map(_ @@ debugResponse)
    _      <- client.request(Request.get("http://jsonplaceholder.typicode.com/todos"))
  } yield ()
```

### Logging Aspects

To log the client interactions, we can use the `ZClientAspect.requestLogging` which logs the request details such as method, duration, url, user-agent, status code and request size.

Let's try an example:

```scala mdoc:compile-only
import zio._
import zio.http._

val loggingAspect =
  ZClientAspect.requestLogging(
    loggedRequestHeaders = Set(Header.UserAgent),
    logResponseBody = true,
  )

val program =
  for {
    client <- ZIO.service[Client].map(_ @@ loggingAspect)
    _      <- client.request(Request.get("http://jsonplaceholder.typicode.com/todos"))
  } yield ()
```

### Follow Redirects

To follow redirects, we can apply the `ZClientAspect.followRedirects` aspect, which takes the maximum number of redirects to follow and a callback function that allows us to customize the behavior when a redirect is encountered:

```scala mdoc:compile-only
import zio._
import zio.http._

val followRedirects = ZClientAspect.followRedirects(3)((resp, message) => ZIO.logInfo(message).as(resp))

for {
  client   <- ZIO.service[Client].map(_ @@ followRedirects)
  response <- client.request(Request.get("http://google.com"))
  _        <- response.body.asString.debug
} yield ()
```

## Configuring ZIO HTTP Client

The ZIO HTTP Client provides a flexible configuration mechanism through the `ZClient.Config` class. This class allows us to customize various aspects of the HTTP client, including SSL settings, proxy configuration, connection pool size, timeouts, and more. The `ZClient.Config.default` provides a default configuration that can be customized using `copy` method or by using the utility methods provided by the `ZClient.Config` class.

Let's take a look at the available configuration options:

- **SSL Configuration**: Allows us to specify SSL settings for secure connections.
- **Proxy Configuration**: Enables us to configure a proxy server for outgoing HTTP requests.
- **Connection Pool Configuration**: Defines the size of the connection pool.
- **Max Initial Line Length**: Sets the maximum length of the initial line in an HTTP request or response. The default is set to 4096 characters.
- **Max Header Size**: Specifies the maximum size of HTTP headers in bytes. The default is set to 8192 bytes.
- **Request Decompression**: Specifies whether the client should decompress the response body if it's compressed.
- **Local Address**: Specifies the local network interface or address to use for outgoing connections. It's set to None, indicating that the client will use the default local address.
- **Add User-Agent Header**: Indicates whether the client should automatically add a User-Agent header to outgoing requests. It's set to true in the default configuration.
- **WebSocket Configuration**: Configures settings specific to WebSocket connections. In this example, the default WebSocket configuration is used.
- **Idle Timeout**: Specifies the maximum idle time for persistent connections in seconds. The default is set to 50 seconds.
- **Connection Timeout**: Specifies the maximum time to wait for establishing a connection in seconds. By default, the client has no connection timeout.

Here are some of the above configuration options in more detail:

### Configuring SSL

The default SSL configuration of `ZClient.Config.default` is `None`. To enable and configure SSL for the client, we can use the `ZClient.Config#ssl` method. This method takes a config of type `ClientSSLConfig` which supports different SSL configurations such as `Default`, `FromCertFile`, `FromCertResource`, `FromTrustStoreFile`, and `FromTrustStoreResource.

Let's see an example of how to configure SSL for the client:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HttpsClient.scala")
```

### Configuring Proxy

To configure a proxy for the client, we can use the `Client#proxy` method. This method takes a `Proxy` and updates the client's configuration to use the specified proxy for all requests:

```scala mdoc:compile-only
import zio._
import zio.http._

val program = for {
  proxyUrl <- ZIO.fromEither(URL.decode("http://localhost:8123"))
  client   <- ZIO.serviceWith[Client](_.proxy(Proxy(url = proxyUrl)))
  res      <- client.request(Request.get("https://jsonplaceholder.typicode.com/todos"))
} yield ()
```

### Connection Pooling

Connection pooling is a crucial mechanism in ZIO HTTP for optimizing the management of HTTP connections. By default, ZIO HTTP uses a fixed-size connection pool with a capacity of 10 connections. This means that the client can maintain up to 10 idle connections to the server for reuse. When the client makes a request, it checks the connection pool for an available connection to the server. If a connection is available, it reuses it for the request. If no connection is available, it creates a new connection and adds it to the pool.

To configure the connection pool, we have to update the `ZClient.Config#connectionPool` field with the preferred configuration. The `ConnectionPoolConfig` trait serves as a base trait for different connection pool configurations. It is a sealed trait with five different implementations:

- `Disabled`: Indicates that connection pooling is disabled.
- `Fixed`: Takes a single parameter, `size`, which specifies a fixed size connection pool.
- `FixedPerHost`: Takes a map of `URL.Location.Absolute` to `Fixed` to specify a fixed size connection pool per host.
- `Dynamic`: Takes three parameters, `minimum`, `maximum`, and `ttl`, to configure a dynamic connection pool with minimum and maximum sizes and a time-to-live (TTL) duration.
- `DynamicPerHost`: Similar to Dynamic, but with configurations per host.

Also the `ZClient.Config` has some utility methods to update the connection pool configuration, e.g. `ZClient.Config#fixedConnectionPool` and `ZClient.Config#dynamicConnectionPool`. Let's see an example of how to configure the connection pool:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ClientWithConnectionPooling.scala")
```

### Enabling Response Decompression

When making HTTP requests using a client, such as a web browser or a custom HTTP client, it's essential to optimize data transfer for efficiency and performance.

By default, most HTTP clients do not advertise compression support when making requests to web servers. However, servers often compress response bodies when they detect that the client supports compression. To enable response compression, we need to add the `Accept-Encoding` header to our HTTP requests. The `Accept-Encoding` header specifies the compression algorithms supported by the client. Common values include `gzip` and `deflate`. When a server receives a request with the `Accept-Encoding` header, it may compress the response body using one of the specified algorithms.

Here's an example of an HTTP request with the Accept-Encoding header:

```http
GET https://example.com/
Accept-Encoding: gzip, deflate
```

When a server responds with a compressed body, it includes the Content-Encoding header to specify the compression algorithm used. The client then needs to decompress the body before processing its contents.

For example, a compressed response might look like this:

```http
200 OK
content-encoding: gzip
content-type: application/json; charset=utf-8

<compressed-body>
```

To decompress the response body with `ZClient`, we need to enable response decompression by using the `ZClient.Config#requestDecompression` method:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ClientWithDecompression.scala")
```

## Customizing `ClientDriver` and `DnsResolver`

Rather than utilizing the default layer, `Client.default`, we have the option to employ the `Client.customized` layer. This layer requires `ClientDriver`, `DnsResolver`, and the `Client.Config` layers:

```scala
object Client {
  val customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client] = ???
}
```

This empowers us to interchange the client driver with alternatives beyond the default Netty driver or to customize it to our specific requirements. Also, we can customize the DNS resolver to use a different DNS resolution mechanism.

## Examples

### Simple Client Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/SimpleClient.scala")
```

### ClientServer Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ClientServer.scala")
```

### Authentication Client Example

This example code demonstrates accessing a protected route in an [authentication server](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/AuthenticationClient.scala) by first obtaining a JWT token through a login request and then using that token to access the protected route:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/AuthenticationClient.scala")
```

### Reconnecting WebSocket Client Example

This example represents a WebSocket client application that automatically attempts to reconnect upon encountering errors or disconnections. It uses the `Promise` to notify about WebSocket errors:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/WebSocketReconnectingClient.scala")
```
