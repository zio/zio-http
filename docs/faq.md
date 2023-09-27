---
id: faq
title: "common zio-http asked questions"
---

Explore the most commonly asked questions about zio-http and find detailed answers in this informative resource.

**Q. What is ZIO-HTTP ?**

ZIO Http is a functional Scala library utilized for constructing high-performance HTTP services and clients. It leverages the power of ZIO's concurrency library and the Netty network library. With ZIO-HTTP, you can create efficient and expressive HTTP applications through its comprehensive high-level API.

<br>

**Q. Is zio-http a library or a framework?**

ZIO-HTTP is primarily a library rather than a framework. It provides a set of tools, components, and abstractions that you can utilize to build HTTP-based services and clients in a functional programming style using Scala and the ZIO concurrency library. It offers a high-level API for constructing HTTP applications, but it does not impose a rigid framework structure or dictate the overall architecture of your application. Instead, it focuses on providing the necessary building blocks and utilities for working with HTTP protocols in a composable and expressive manner.

<br>

**Q. Does ZIO-HTTP provide support for an asynchronous programming model?**

Yes, ZIO-HTTP supports an asynchronous model for handling HTTP requests and responses. It is built on top of the ZIO concurrency library, which provides powerful asynchronous and concurrent programming capabilities.

ZIO's concurrency model is designed to handle high scalability and performance requirements. It utilizes lightweight fibers for efficient concurrency management, allowing you to handle a large number of concurrent requests without incurring significant overhead. By leveraging the asynchronous nature of ZIO, you can write non-blocking and highly performant code, which is essential for building webscale applications.

With ZIO-HTTP, you can take advantage of these asynchronous features and design your applications to handle high loads, making it well-suited for webscale scenarios. Checkout the [benachmark results](https://web-frameworks-benchmark.netlify.app/compare?f=zio-http)  To assess how ZIO-HTTP compares to other JVM-based web libraries in relation to their synchronous and asynchronous capabilities.

<br>

**Q. Does ZIO-HTTP support middleware for request/response modification?**

Yes, ZIO-HTTP does support middleware for request/response modification. Middleware in ZIO-HTTP allows you to intercept and modify requests and responses as they flow through the application's request/response pipeline.

You can define custom middleware functions that can perform operations such as request/response transformation, authentication, logging, error handling, and more. Middleware functions can be composed and applied to specific routes or globally to the entire application.

<details>

<summary><b>Example</b></summary>

```scala mdoc:silent:reset
package example

import java.util.concurrent.TimeUnit

import zio._

import zio.http._

object HelloWorldWithMiddlewares extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    // this will return result instantly
    case Method.GET -> Root / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> Root / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val serverTime: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] = HttpAppMiddleware.patchZIO(_ =>
    for {
      currentMilliseconds <- Clock.currentTime(TimeUnit.MILLISECONDS)
      withHeader = Response.Patch.addHeader("X-Time", currentMilliseconds.toString)
    } yield withHeader,
  )
  
  val middlewares =
    // print debug info about request and response
    HttpAppMiddleware.debug ++
      // close connection if request takes more than 3 seconds
      HttpAppMiddleware.timeout(3 seconds) ++
      // add static header
      HttpAppMiddleware.addHeader("X-Environment", "Dev") ++
      // add dynamic header
      serverTime

  // Run it like any simple app
  val run = Server.serve((app @@ middlewares).withDefaultErrorResponse).provide(Server.default)
}
```

</details>  
