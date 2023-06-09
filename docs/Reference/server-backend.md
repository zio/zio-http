
# ZIO HTTP Server Configurations

This section describes how to start a ZIO HTTP server using default and custom configurations.

## Start a ZIO HTTP Server with Default Configurations

Here's an example of starting a ZIO HTTP server with default configurations:

```scala
import zio.http._
import zio._

// Define your app using suitable middleware and routes
def app: HttpApp[Any, Nothing] = ???

// Create a server instance with default configurations
val server = Server.serve(app).provide(Server.default)
```

The `Server.default` method returns a `ZLayer[Any, Throwable, Server.Server]` layer that provides the default environment for the server instance. The `provide` method attaches this default environment to the server instance.

### Customizing the Port

If you want to customize the port of the server, use the `Server.defaultWithPort(port)` method. Here's an example:

```scala
// Create a server instance with customized port configuration
val server = Server.serve(app).provide(Server.defaultWithPort(8081))
```

In this case, the default server environment is extended with an additional setting to customize the port configuration.

### Customizing More Properties of the Default Configuration

To customize more properties of the default server configuration, use the `Server.defaultWith(callback)` method, where `callback` is a callback function that applies multiple settings to the underlying configuration object. Here's an example:

```scala
// Create a server instance with more customized configurations
val server = Server.serve(app).provide(
  Server.defaultWith(_.port(8081).enableRequestStreaming)
)
```

In this case, the default server environment is extended with a callback function that sets the port configuration to 8081 and enables request streaming.

## Start a ZIO HTTP Server with Custom Configurations

To start a server with custom configurations, you need to provide a `Server.Config` object that holds the custom configuration, and attach it to the `Server.live` layer. Here's an example:

```scala
// Define your app using suitable middleware and routes
def app: HttpApp[Any, Nothing] = ???

// Create a server instance with custom configurations
val server = Server.serve(app).provide(
  ZLayer.succeed(Server.Config.default.port(8081)),
  Server.live
)
```

In this case, we're passing a `Server.Config` object with a customized port configuration to the `ZLayer.succeed` method along with the `Server.live` layer to create a custom environment for the server instance.

### Loading Configurations from ZIO Configuration Provider

Alternatively, you can use the `Server.configured()` method to load the server configuration from the application's ZIO configuration provider. Here's an example:

```scala
// Define your app using suitable middleware and routes
def app: HttpApp[Any, Nothing] = ???

// Create a server instance with loaded configurations
val server = Server.serve(app).provide(Server.configured())
```

In this case, the `Server.configured()` method loads the server configuration using the application's ZIO configuration provider, which is using the environment by default but can be attached to different backends using the ZIO Config library.

## Customizing Netty-Specific Properties

Finally, to customize Netty-specific properties, use a customized layer that contains both the `Server.Config` and `NettyConfig` objects. Here's an example:

```scala
import zio.duration._
import zio.nio.core.ChannelOptions
import zio.{Has, ZIO, ZLayer}
import zio.netty.eventloop.EventLoopGroup
import zio.netty.{EventLoopGroupProvider, ServerChannelFactory}
import zio.scheduler.Scheduler

// Define your app using suitable middleware and routes
def app: HttpApp[Any, Nothing] = ???

// Define a customized layer that contains both Server.Config and NettyConfig objects
val customLayer: ZLayer[Any, Throwable, Has[NettyConfig] with Has[Server.Config]] =
  (EventLoopGroup.auto(threads = 1) ++ EventLoopGroupProvider.auto ++ Scheduler.live).map {
    case (eventLoopGroup, eventLoopGroupProvider, scheduler) =>
      NettyConfig(
        channelFactory = ServerChannelFactory(),
        channelOptions = ChannelOptions.empty,
        eventLoopGroup = eventLoopGroup,
        eventLoopGroupProvider = eventLoopGroupProvider,
        maxInitialLineLength = 4096,
        maxHeaderSize = 8192,
        maxChunkSize = 8192,
        idleTimeout = 60.seconds,
        scheduler = scheduler
      ) ++ Server.Config.default.port(8081)
  }

// Create a server instance with customized Netty configurations
val server = Server.serve(app).provideCustomLayer(customLayer)
```

In this example, we're defining a customized layer that includes the `NettyConfig` and `Server.Config` objects. The `NettyConfig` object specifies various Netty-specific properties such as channel factory, channel options, event loop group, timeouts, etc. The `Server.Config` object specifies the port configuration. Finally, we're using the `provideCustomLayer` method to attach the custom environment to the server instance.