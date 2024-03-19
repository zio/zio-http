---
id: server
title: Server
---

Using the ZIO HTTP Server, we can serve one or more HTTP applications. It provides methods to install HTTP applications into the server. Also it offers a comprehensive `Config` class that allows fine-grained control over server behavior. We can configure settings such as SSL/TLS, address binding, request decompression and response compression, and more.

This section describes, ZIO HTTP Server and different configurations you can provide while creating the Server:

## Starting a Server with Default Configurations

Assuming we have written an `HttpApp`:

```scala mdoc:silent
import zio.http._
import zio._

def app: HttpApp[Any] = 
  Routes(
    Method.GET / "hello" -> 
      handler(Response.text("Hello, World!"))
  ).toHttpApp
```

We can serve it using the `Server.serve` method:

```scala mdoc:silent
Server.serve(app).provide(Server.default)
```

By default, it will start the server on port `8080`. A quick shortcut to only customize the port is `Server.defaultWithPort`:

```scala mdoc:compile-only
Server.serve(app).provide(Server.defaultWithPort(8081))
```

Or to customize more properties of the _default configuration_:

```scala mdoc:compile-only
Server.serve(app).provide(
  Server.defaultWith(
    _.port(8081).enableRequestStreaming
  )
)
```

:::note
Sometimes we may want to have more control over installation of the http application into the server. In such cases, we may want to use the `Server.install` method. This method only installs the `HttpApp` into the server, and the lifecycle of the server can be managed separately.
:::

## Starting a Server with Custom Configurations

The `live` layer expects a `Server.Config` holding the custom configuration for the server:

```scala mdoc:compile-only
Server
  .serve(app)
  .provide(
    ZLayer.succeed(Server.Config.default.port(8081)),
    Server.live
  )
```

## Integration with ZIO Config

The `Server` module has a predefined config description, i.e. `Server.Config.config`, that can be used to load the server configuration from the environment, system properties, or any other configuration source.

The `configured` layer loads the server configuration using the application's _ZIO configuration provider_, which is using the environment by default but can be attached to a different backends using the [ZIO Config library](https://zio.github.io/zio-config/).

```scala mdoc:compile-only
Server
  .serve(app)
  .provide(
    Server.configured()
  )
```

For example, to load the server configuration from the hocon file, we should add the `zio-config-typesafe` dependency to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-config"          % "<version>",
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % "<version>",
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % "<version>",
```

And put the `application.conf` file in the `src/main/resources` directory:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/resources/application.conf")
```

Then we can load the server configuration from the `application.conf` file using the `ConfigProvider.fromResourcePath()` method:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ServerConfigurationExample.scala")
```

## Configuring SSL

By default, the server is not configured to use SSL. To enable it, we need to update the server config, and use the `Server.Config#ssl` field to specify the SSL configuration:

```scala mdoc:compile-only
import zio.http._

val sslConfig = SSLConfig.fromResource(
  behaviour = SSLConfig.HttpBehaviour.Accept,
  certPath = "server.crt",
  keyPath = "server.key",
)

val config = Server.Config.default
  .ssl(sslConfig)
```

Here is the full example of how to configure SSL:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HttpsHelloWorld.scala")
```

## Enabling Response Compression

Response compression is a crucial technique for optimizing data transfer efficiency and improving performance in web applications. By compressing response bodies, servers can significantly reduce the amount of data sent over the network, leading to faster loading times and better user experiences.

To enable response compression, it's essential to configure both the server and the client correctly. On the server side, we need to ensure that our web server is properly configured to compress outgoing responses. 

On the client side, we need to indicate to the server that we support response compression by including the `Accept-Encoding` header in our HTTP requests. The `Accept-Encoding` header specifies the compression algorithms that the client can handle, such as `gzip` or `deflate`. When the server receives a request with the `Accept-Encoding` header, it can compress the response body using one of the supported algorithms before sending it back to the client.

Here's an example of how to include the `Accept-Encoding` header in an HTTP request:

```http
GET https://example.com/
Accept-Encoding: gzip, deflate
```

When the server responds with a compressed body, it includes the `Content-Encoding` header in the response to indicate the compression algorithm used. The client then needs to decompress the response body before processing its contents.

For instance, a compressed response might have headers like this:

```http
200 OK
Content-Encoding: gzip
Content-Type: application/json; charset=utf-8
<compressed-body>
```

In ZIO HTTP, response compression is disabled by default. To enable it, we need to update the server config, i.e. `Server.Config`, and use the `responseCompression` field to specify the compression configuration:

```scala mdoc:compile-only
import zio.http._

val config = 
  Server.Config.default.copy(
    responseCompression = Some(Server.Config.ResponseCompressionConfig.default),
  )
```

Here is the full example of how to enable response compression:

```scala mdoc:passthrough
import zio.http._

printSource("zio-http-example/src/main/scala/example/ServerResponseCompression.scala")
```

After running the server, we can test it using the following `curl` command:

```bash
 curl -X GET http://localhost:8080/hello -H "Accept-Encoding: gzip" -i --output response.bin
```

The `response.bin` file will contain the compressed response body.

## Enabling Streaming of Request Bodies

Enabling streaming of request bodies in the ZIO HTTP server typically involves configuring the server to handle incoming requests asynchronously and process request bodies as they arrive, rather than waiting for the entire request body to be received before processing begins. 

There are two streaming methods available on request bodies: `Body#asStream` and `Body#asMultipartFormStream`. When we receive a request with a streaming body, whether it's a single part or a multipart form, if the server is configured to handle request streaming, we can process the body as a stream of bytes, which allows us to handle large request bodies more efficiently.

By default, request streaming is disabled in ZIO HTTP. To enable it, we need to update the server config with the `Server.Config#enableRequestStreaming` method.

The following example demonstrates a server that handles streaming request bodies:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.stream.{ZSink, ZStream}

object RequestStreamingServerExample extends ZIOAppDefault {
  def logBytes = (b: Byte) => ZIO.log(s"received byte: $b")

  private val app: HttpApp[Any] =
    Routes(
      Method.POST / "upload-stream" / "simple"     -> handler { (req: Request) =>
        for {
          count <- req.body.asStream.tap(logBytes).run(ZSink.count)
          _     <- ZIO.debug(s"Read $count bytes")
        } yield Response.text(count.toString)
      },
      Method.POST / "upload-stream" / "form-field" -> handler { (req: Request) =>
        if (req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`))
          for {
            _     <- ZIO.debug("Starting to read multipart/form stream")
            form  <- req.body.asMultipartFormStream
              .mapError(ex =>
                Response(
                  Status.InternalServerError,
                  body = Body.fromString(s"Failed to decode body as multipart/form-data (${ex.getMessage}"),
                ),
              )
            count <- form.fields
              .tap(f => ZIO.log(s"started reading new field: ${f.name}"))
              .flatMap {
                case sb: FormField.StreamingBinary =>
                  sb.data.tap(logBytes)
                case _                             =>
                  ZStream.empty
              }
              .run(ZSink.count)

            _ <- ZIO.debug(s"Finished reading multipart/form stream, received $count bytes of data")
          } yield Response.text(count.toString)
        else ZIO.succeed(Response(status = Status.NotFound))
      },
    ).sandbox.toHttpApp @@ Middleware.debug

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    Server
      .serve(app)
      .provide(
        ZLayer.succeed(Server.Config.default.enableRequestStreaming),
        Server.live,
      )
}
```

To test the 'upload-stream/simple' endpoint, let's run the following client code:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.stream.ZStream

object SimpleStreamingClientExample extends ZIOAppDefault {
  val app = for {
    url    <- ZIO.fromEither(URL.decode("http://localhost:8080/upload-stream"))
    client <- ZIO.serviceWith[Client](_.url(url) @@ ZClientAspect.requestLogging())
    res    <- client.request(
      Request.post(
        path = "simple",
        body = Body.fromStreamChunked(
          ZStream.fromIterable("Let's send this text as a byte array".getBytes()),
        ),
      ),
    )
    _      <- ZIO.debug(res.status)

  } yield ()

  def run = app.provide(Client.default, Scope.default)
}
```

We will see that the server logs the received bytes as they arrive, and the client will receive the response with the number of bytes received.

The `upload-stream/form-field` endpoint is designed to handle multipart form data. To test it, we can use the following client code:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.stream.ZStream

object FormFieldStreamingClientExample extends ZIOAppDefault {
  val app = for {
    url    <- ZIO.fromEither(URL.decode("http://localhost:8080/upload-stream"))
    client <- ZIO.serviceWith[Client](_.url(url) @@ ZClientAspect.requestLogging())
    form = Form(
      Chunk(
        ("foo", "This is the first part of the foo form field."),
        ("foo", "This is the second part of the foo form field."),
        ("bar", "This is the body of the bar form field."),
      ).map { case (name, data) =>
        FormField.streamingBinaryField(
          name = name,
          data = ZStream.fromChunk(Chunk.fromArray(data.getBytes)).schedule(Schedule.fixed(200.milli)),
          mediaType = MediaType.application.`octet-stream`,
        )
      },
    )
    res <- client.request(
      Request
        .post(
          path = "form-field",
          body = Body.fromMultipartForm(form, Boundary("boundary123")),
        ),
    )
    _ <- ZIO.debug(res.status)

  } yield ()

  def run = app.provide(Client.default, Scope.default)
}
```

The server will log the received form fields as they arrive, and the client will receive the response with the number of bytes received.

## Serving on Any Open Port

If we want to start the server on any open port, we can use the `Server.Config#onAnyOpenPort` method:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ServeOnAnyOpenPort.scala")
```

## Graceful Shutdown Configuration

When a ZIO HTTP server is running, it handles incoming requests from clients, processes them, and sends back appropriate responses. In-flight requests are requests that have been received by the server but have not yet been fully processed or responded to. These requests might be in various stages of processing, such as waiting for database queries to complete or for resources to become available.

When we're shutting down the server, it's important to handle these in-flight requests gracefully. ZIO‌ HTTP has a built-in mechanism to allow in-flight requests to finalize before shutting down the server. The default behavior is to wait for 10 seconds for in-flight requests to finalize before shutting down the server. During this time, the server will not accept new requests, but it will continue to process existing requests until they're fully completed.

To change the default graceful shutdown timeout, we can use the `Server.Config#gracefulShutdownTimeout` method. It takes a `Duration` as an argument, and returns a new `Server.Config` with the specified graceful shutdown timeout:

```scala mdoc:compile-only
import zio.http._

val config = Server.Config.default.gracefulShutdownTimeout(20.seconds)
```

In the following example, we can test such behavior by sending a request to the server and while the server is processing the request, we interrupt the server, and we will see that the server will wait for the request to be processed before shutting down:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/GracefulShutdown.scala")
```

This approach ensures that clients receive appropriate responses for their requests, rather than encountering errors or abrupt disconnections. It helps maintain the integrity of the communication between clients and the server, providing a smoother experience for users and preventing potential data loss or corruption.

## Idle Timeout Configuration

The idle timeout is a mechanism by which the server automatically terminates an inactive connection after a certain period of inactivity. When a client connects to the server, it establishes a connection to request and receive responses. However, there may be instances where the client becomes slow, inactive, or unresponsive, and the server needs to reclaim resources associated with idle connections to optimize server performance and resource utilization.

By default, ZIO HTTP does not have an idle timeout configured. To enable it, we can use the `Server.Config#idleTimeout` method. It takes a `Duration` as an argument, and returns a new `Server.Config` with the specified idle timeout:

```scala mdoc:compile-only
import zio.http._

val config = Server.Config.default.idleTimeout(60.seconds)
```

For example, if a server has an idle timeout set to 60 seconds, any connection that remains idle (i.e., without any data being sent or received) for more than 60 seconds will be automatically terminated by the server.

## Keep-Alive Configuration

Typically, in HTTP 1.0, each request/response pair requires a separate TCP connection, which can lead to increased overhead due to the establishment and teardown of connections for each interaction. So assuming the following HTTP Application:

```scala mdoc:compile-only
import zio._
import zio.http._

object KeepAliveExample extends ZIOAppDefault {
  val httpApp = handler(Response.text("Hello World!")).toHttpApp

  override val run =
    Server.serve(httpApp).provide(Server.default)
}
```

When we send the following request to the server, the server will respond with `Connection: close` header:

```bash
$ curl --http1.0 localhost:8080 -i
HTTP/1.1 200 OK
content-type: text/plain
content-length: 12
connection: close

Hello World!⏎
```

However, with the `Connection: Keep-Alive` header, the client can request that the connection remain open after the initial request, allowing for subsequent requests to be sent over the same connection without needing to establish a new one each time.

```bash
$ curl --http1.0 localhost:8080 -i -H "connection: keep-alive"
HTTP/1.1 200 OK
content-type: text/plain
content-length: 12
```

In HTTP 1.1, persistent connections are the default behavior, so the `Connection: Keep-Alive` header is often unnecessary:

```bash
$ curl --http1.1 localhost:8080 -i
HTTP/1.1 200 OK
content-type: text/plain
content-length: 12
```

However, it can still be used to override the default behavior or to provide additional parameters related to connection management. In the following example, we are going to ask the server to close the connection after serving the request:

```bash
$ curl --http1.1 localhost:8080 -i -H "Connection: close"
HTTP/1.1 200 OK
content-type: text/plain
content-length: 12
connection: close

Hello World!⏎
```

The‌ ZIO HTTP server by default supports keep-alive connections. To disable it, we can use the `Server.Config#keepAlive` method, by setting it to `false`:

```scala mdoc:compile-only
import zio.http._

val config = Server.Config.default.keepAlive(false)
```

## Netty Configuration

In order to customize Netty-specific properties, the `customized` layer can be used, providing not only `Server.Config`
but also `NettyConfig`.