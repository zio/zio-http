---
sidebar_position: "1"
---
# Server

This section describes, ZIO HTTP Server and different configurations you can provide while creating the Server 

## Start a ZIO HTTP Server with default configurations
```scala
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
```
## Start a ZIO HTTP Server with custom configurations.
1. Imports required by the customised server. 
    ```scala
    import zio.http._
    import zio.http.service.ServerChannelFactory
    import zio.http.service.{EventLoopGroup, Server}
    import zio._
    import scala.util.Try
    ```
2. The Server can be built incrementally with a `++` each returning a new Server overriding any default configuration. (More properties are given in the [Server Configurations](#server-configurations) section below.)
    ```scala
    private val server =
      Server.port(PORT) ++              // Setup port
        Server.maxRequestSize(8 * 1024) ++ // handle max request size of 8 KB (default 4 KB)
        Server.app(fooBar ++ app)       // Setup the Http app
    ```
3. And then use ```Server.make``` to get a "managed" instance use it to run a server forever
    ```scala
    override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
      server.make
        .use(start =>
          console.putStrLn(s"Server started on port ${start.port}")
          *> ZIO.never,
        ).provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(2))
        .exitCode
    ```
   **Tip :** `ServerChannelFactory.auto ++ EventLoopGroup.auto(num Threads)` is supplied as an external dependency to choose netty transport type. One can leave it as `auto` to let the application handle it for you. 
   Also in `EventLoopGroup.auto(numThreads)` you can choose number of threads based on number of available processors. 

### Binding Server to a socket address
One can bind server to Inet address in multiple ways, either by providing a port number or 
- If no port is provided, the default port is 8080
- If specified port is 0, it will use a dynamically selected port.

<details>
<summary><b>A complete example </b></summary>

- Example below shows how the server can be started in forever mode to serve HTTP requests:

```scala
import zio.http._
import zio.http.service._
import zio.http.service.ServerChannelFactory
import zio._

import scala.util.Try

object HelloWorldAdvanced extends App {
  // Set a port
  private val PORT = 8090

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val app = Http.collectM[Request] {
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text)
    case Method.GET -> !! / "utc"    => clock.currentDateTime.map(s => Response.text(s.toString))
  }

  private val server =
    Server.port(PORT) ++              // Setup port
            Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
            Server.app(fooBar +++ app)      // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    server.make
            .use(_ =>
              // Waiting for the server to start
              console.putStrLn(s"Server started on port $PORT")

                      // Ensures the server doesn't die after printing
                      *> ZIO.never,
            )
            .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(nThreads))
            .exitCode
  }
}
 ```
</details>

## Server Configurations

| **Configuration**              | **Purpose and usage**          |
| -----------                    | -----------                    |
| `Server.app(httpApp)`          | Mount routes. Refer to complete example above                               |
| `Server.maxRequestSize(8 * 1024)`          | handle max request size of 8 KB (default 4 KB)                               |
| `Server.port(portNum)` or `Server.bind(portNum)`       | Bind server to the port, refer to examples above                               |
| `Server.ssl(sslOptions)`       | Creates a new server with ssl options. [HttpsHelloWorld](https://github.com/zio/zio-http/blob/main/example/src/main/scala/example/HttpsHelloWorld.scala)                               |
| `Server.acceptContinue`        | Sends a [100 CONTINUE](https://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html#sec8.2.3)                              |
| `Server.disableFlowControl`    | Refer [Netty FlowControlHandler](https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html)                               |
| `Server.disableLeakDetection`  | Disable any leak detection Refer netty's [ResourceLeakDetector](https://netty.io/4.0/api/io/netty/util/ResourceLeakDetector.Level.html)                               |
| `Server.simpleLeakDetection`   | Simplistic leak detection comes with small over head. Refer netty's [ResourceLeakDetector](https://netty.io/4.0/api/io/netty/util/ResourceLeakDetector.Level.html)                               |
| `Server.paranoidLeakDetection` | Comes with highest possible overhead (for testing purposes only). Refer netty's [ResourceLeakDetector](https://netty.io/4.0/api/io/netty/util/ResourceLeakDetector.Level.html)                              |
| `Server.consolidateFlush`      | Flushing content is done in batches. Can potentially improve performance.                               |
