---
id: server
title: Server
---

## Configuring ZIO HTTP Server

This section describes, ZIO HTTP Server and different configurations you can provide while creating the Server

### Default Configurations

Assume we have an `HttpApp`:

```scala mdoc:silent
import zio.http._
import zio._

def app: HttpApp[Any] = ???
```

We can start a ZIO HTTP Server with default configurations using `Server.serve`:

```scala mdoc:compile-only
Server.serve(app).provide(Server.default)
```

A quick shortcut to only customize the port is `Server.defaultWithPort`:

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

### Custom Configurations

The `live` layer expects a `Server.Config` holding the custom configuration for the server.

```scala mdoc:compile-only
Server
  .serve(app)
  .provide(
    ZLayer.succeed(Server.Config.default.port(8081)),
    Server.live
  )
```

The `configured` layer loads the server configuration using the application's _ZIO configuration provider_, which
is using the environment by default but can be attached to a different backends using
the [ZIO Config library](https://zio.github.io/zio-config/).

```scala mdoc:compile-only
Server
  .serve(app)
  .provide(
    Server.configured()
  )
```

In order to customize Netty-specific properties, the `customized` layer can be used, providing not only `Server.Config`
but also `NettyConfig`.
