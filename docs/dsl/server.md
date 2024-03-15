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

The `Server` module has a predefined config description, i.e. [`Server.Config.config`](server_config.md), that can be used to load the server configuration from the environment, system properties, or any other configuration source.

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

## Netty Configuration

In order to customize Netty-specific properties, the `customized` layer can be used, providing not only `Server.Config`
but also `NettyConfig`.