---
id: server
title: "Server in ZIO HTTP"
---

# Server

Setting up servers to handle HTTP requests is a critical part of development. ZIO HTTP simplifies this process by providing an intuitive way to create and run servers that efficiently handle incoming requests.

## Starting an HTTP Server

To launch an HTTP server in ZIO HTTP, use the `Server.start` method. This method allows us to specify the port number and the HTTP app to serve.

```scala
import zio._
import zio.http._
import zio.http.Server


object HelloWorld extends App {
  val app = Http.ok

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
```

In this example, a simple HelloWorld app responds with empty content and a 200 status code. The server is deployed on port 8090 using `Server.start`.

## Server Configuration

ZIO HTTP allows us to configure server according to various parameters such as host, port, leak detection level, request size and address. This ensures server operates optimally based on your specific requirements.

```scala
import zio.http._
import zio.http.Server

object HelloWorld extends App {
  val app = Http.ok

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server
      .start(
        port = 8090,
        app = app,
        .maxRequestSize(16 * 1024) // Increase max request size to 16 KB
        leakDetectionLevel = LeakDetectionLevel.Unchecked
      )
      .exitCode
}
```
This example configures the simple server to operate with unchecked leak detection level. We can customize other settings as needed.



