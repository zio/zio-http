---
id: client
title: Client
---

# Client 

The client concept in ZIO-HTTP represents the ability to interact with remote HTTP servers by sending requests and handling the received responses. It allows you to build robust and scalable HTTP clients using ZIO's functional programming capabilities.

Key components and concepts involved in the client concept in ZIO-HTTP:

- Client Creation: To create an HTTP client, you can use the `Client.request` method. This method takes a URL, additional headers, and other parameters, and returns a ZIO effect representing the client's interaction with the server.

- Request Building: ZIO-HTTP provides convenient functions to construct HTTP requests, including specifying the HTTP method (GET, POST, etc.), URL path, request headers, and request body.

- Response Handling: Once a request is sent, the client receives a response from the server. ZIO-HTTP allows you to handle the response using various combinators and methods. For example, you can extract the response body as a string, parse it as JSON, or process it as a stream of bytes.

- Client Configuration: ZIO-HTTP allows you to configure the behavior of the client through a `ZClient.Config` object. This configuration includes options such as request timeouts, follow redirects, SSL settings, and more.

- Dependency Injection: ZIO's dependency injection capabilities enable you to provide the necessary dependencies to the client. This includes the client configuration, the client implementation, and other dependencies required for networking, DNS resolution, and other underlying functionality.

- Error Handling: ZIO-HTTP provides comprehensive error handling mechanisms. Errors that may occur during the client interaction, such as network failures, timeouts, or invalid responses, are represented as typed errors in the ZIO effect. You can handle these errors using combinators like `catchAll`, `orElse`, or `fold`.

By leveraging the client concept in ZIO-HTTP, you can build type-safe, composable, and concurrent HTTP clients that seamlessly integrate with the ZIO ecosystem. It enables you to write pure and testable code while benefiting from ZIO's powerful features like error handling, concurrency, and resource management.

here is an example:

```scala
package example

import zio._

import zio.http.Header.AcceptEncoding
import zio.http.netty.NettyConfig
import zio.http.{Client, DnsResolver, Headers, ZClient}

object ClientWithDecompression extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url, headers = Headers(AcceptEncoding(AcceptEncoding.GZip(), AcceptEncoding.Deflate())))
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  val config       = ZClient.Config.default.requestDecompression(true)
  override val run =
    program.provide(
      ZLayer.succeed(config),
      Client.live,
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
    )

}
```

**Code break down**
    
- Client Creation: The code creates an HTTP client using the `Client.request` method. It sends an HTTP GET request to the specified URL (`url` variable) and provides additional headers (Accept-Encoding) for the request.

- Request and Response Handling: The code retrieves the response from the client using `res.body.asString`, which reads the response body as a string. It then proceeds to print the response data using `Console.printLine`.

- Client Configuration: The code includes a `ZClient.Config` object that configures the client. In this case, `requestDecompression` is set to `true`, enabling automatic decompression of the response body if the server provides compressed data (e.g., gzip or deflate).

- Client Execution: The `program` is executed using the `run` method, which provides the necessary dependencies to the client. The dependencies include the client configuration, the live `Client` implementation, the `NettyConfig` for the underlying networking library, and the default `DnsResolver`.

Overall, this code demonstrates how to create an HTTP client using ZIO-HTTP. It configures the client, sends an HTTP request, handles the response, and prints the response data. The client concept in ZIO-HTTP enables you to interact with remote servers, send requests, and process the received responses in a purely functional manner using ZIO's effectful programming model.