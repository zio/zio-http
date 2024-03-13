---
id: client
title: Client
---

`ZClient` is an HTTP client that enables us to make HTTP requests and handle responses in a purely functional manner. ZClient leverages the ZIO library's capabilities to provide a high-performance, asynchronous, and type-safe HTTP client solution.

## Key Features

**Purely Functional**: ZClient is built on top of the ZIO library, enabling a purely functional approach to handling HTTP requests and responses. This ensures referential transparency and composability, making it easy to build and reason about complex HTTP interactions.

**Type-Safe**: ZClient's API is designed to be type-safe, leveraging Scala's type system to catch errors at compile time and provide a seamless development experience. This helps prevent common runtime errors and enables developers to write robust and reliable HTTP client code.

**Asynchronous & Non-blocking**: ZClient is fully asynchronous and non-blocking, allowing you to perform multiple HTTP requests concurrently without blocking threads. This ensures optimal resource utilization and scalability, making it suitable for high-performance applications.

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
To learn more about resource management and `Scope` in ZIO, refer to the [dedicated guide on this topic](https://zio.dev/reference/resource/scope) in the ZIO Core documentation.
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
  val program: ZIO[Client & Scope, Throwable, Unit] = 
    for {
      res   <- Client.request(Request.get("http://jsonplaceholder.typicode.com/todos"))
      todos <- res.body.to[List[Todo]]
      _     <- Console.printLine(s"The first task is '${todos.head.title}'")
    } yield ()

  override val run = program.provide(Client.default, Scope.default)
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
  val program: ZIO[Client & Scope, Throwable, Unit] = 
    for {
      res   <- Client.request(Request.get("http://jsonplaceholder.typicode.com/todos"))
      todos <- res.body.to[List[Todo]]
      _     <- Console.printLine(s"The first task is '${todos.head.title}'")
    } yield ()

  override val run = program.provide(Client.default, Scope.default)
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
```composability

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

  val app: ZIO[Client with Scope, Throwable, Unit] =
    for {
      url    <- ZIO.fromEither(URL.decode("ws://ws.vi-server.org/mirror"))
      client <- ZIO.serviceWith[Client](_.url(url))
      _      <- client.socket(socketApp)
      _      <- ZIO.never
    } yield ()

  val run: ZIO[Any, Throwable, Any] =
    app.provide(Client.default, Scope.default)

}
```

In the above example, we defined a WebSocket client that connects to a mirror server and sends and receives messages. When the connection is established, it receives the `UserEvent.HandshakeComplete` event and then it sends a "foo" message to the server. Consequently, the server sends a "foo" message, and the client responds with a "bar" message. Finally, the server sends a "bar" message, and the client closes the connection.

## Configuring Proxy

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
To learn more about headers and how they work, check out our dedicated section called [Header Operations](./headers.md#headers-operations) on the headers page.
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

val program: ZIO[Scope with Client, Throwable, Unit] =
  for {
    url    <- ZIO.fromEither(URL.decode("http://localhost:8080"))
    client <- ZIO.serviceWith[Client](_.url(url))
    _      <- client.post("/users")(Body.from(User("John", 42)))
    res    <- client.get("/users")
    _      <- client.delete("/users/1")
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
      _      <- client.request(Request.get("http://jsonplaceholder.typicode.com/todos"))
    } yield ()

  override val run = program.provide(Client.default, Scope.default)
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

## Enabling Response Decompression

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
