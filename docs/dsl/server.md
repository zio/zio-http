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

## Netty Configuration

In order to customize Netty-specific properties, the `customized` layer can be used, providing not only `Server.Config`
but also `NettyConfig`.