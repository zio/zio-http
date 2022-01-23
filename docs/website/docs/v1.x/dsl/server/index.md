# Running HTTP Server and serving requests

This section describes, how to build an HttpApp, a collection of "routes", and then how it is bound to a "port" to serve HTTP requests. 

## Building and running a simple Server
- First, the usual imports
```scala
import zhttp.http._
import zhttp.service.Server
import zio._
```
- Build an HttpApp with a set of routes
```scala
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }
```
- As a convenience, zio provides an `App` trait with the main function of the application which returns `URIO[ZEnv, ExitCode]`. Directly use `Server.start` specifying port and mount the app to start the server
```scala
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
```
## Building and running a Server in "forever" mode, with custom configurations.
1. Imports required by the advanced server. 
    ```scala
    import zhttp.http._
    import zhttp.service.server.ServerChannelFactory
    import zhttp.service.{EventLoopGroup, Server}
    import zio._
    import scala.util.Try
    ```
2. The Server can be built incrementally with a `++` each returning a new Server overriding any default configuration.
    ```scala
    private val server =
      Server.port(PORT) ++              // Setup port
        Server.maxRequestSize(8 * 1024) ++ // handle max request size of 8 KB (default 4 KB)
        Server.app(fooBar ++ app)       // Setup the Http app
    ```
  More properties are given in the [Server Configurations](#server-configurations) section below.
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
   **Note**`ServerChannelFactory.auto ++ EventLoopGroup.auto(num Threads)` is supplied as an external dependency to choose netty transport type. One can leave it as `auto` to let the application handle it for you. 
   Also in `EventLoopGroup.auto(numThreads)` you can choose number of threads based on number of available processors. 

### Binding Server to a socket address
One can bind server to Inet address in multiple ways, either by providing a port number or 
- If no port is provided, the default port is 8080
- If port is 0, it will use a dynamically selected port.

<details>
<summary><b>A complete example </b></summary>

- Example below shows how the server can be started in forever mode to serve HTTP requests:

```scala
import zhttp.http._
import zhttp.service._
import zhttp.service.server.ServerChannelFactory
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

| **Configuration**    | **Purpose and usage**         |
| ----------- | ----------- |
| `Server.ssl(sslOptions)`       |        |
| `Server.acceptContinue`   |        |
| `Server.disableFlowControl` | |
| `Server.disableLeakDetection` | |
| `Server.simpleLeakDetection` | |
| `Server.paranoidLeakDetection` | |
| `Server.disableFlowControl` | |
| `Server.consolidateFlush` | |
