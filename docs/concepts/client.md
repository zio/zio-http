---
id: client
title: "Client"
---

# Client

ZIO HTTP empowers us to interact with remote HTTP servers by sending requests and handling responses. This enables us to build robust and scalable HTTP clients using ZIO's functional programming concepts.

## Creating an HTTP Client

ZIO HTTP provides an intuitive way to create HTTP clients using the `HttpClientBuilder` class. This builder allows us to specify various configuration options(optionally) for the client, such as connection timeouts, proxy settings and SSL configurations.
```scala mdoc:silent 

import zio.http._
import zio._

val client: HttpClient = HttpClientBuilder()
  .connectTimeout(Duration.fromMillis(5000))  // Set connection timeout to 5 seconds
  .proxy("localhost", 8888)                 // Use proxy server on localhost:8888
  .ssl()                                     // Enable SSL
  .build
```
In this example, created an HTTP client with a connection timeout of 5 seconds, using a proxy server running on localhost at port 8888 and enabling SSL.

## Making HTTP Requests

Once you have an HTTP client, you can use it to make various types of requests (GET, POST, PUT, DELETE, etc.) to external services. ZIO HTTP provides methods for constructing and sending requests.

```scala mdoc:silent 

import zio.http._
import zio._

val client: HttpClient = ??? // Replace with your configured client

val request = Request.get("https://api.example.com/data")

val program: ZIO[Any, Throwable, Response] = client.send(request)
```
In this example,created a GET request to the `https://api.example.com/data` endpoint and send it using the HTTP client. The send method returns a Response representing the server's response to the request.

## Handling Responses:

After sending a request, you can handle the response returned by the server. ZIO HTTP provides various methods for processing response data, such as reading headers, accessing the body and handling status codes.

```scala mdoc:silent 

import zio.http._
import zio._

val client: HttpClient = ???

val request: Request = Request.get(uri"https://api.example.com/data")

val program: ZIO[Any, Throwable, String] =
  client
    .send(request)
    .flatMap(response => response.bodyAsString)
```

In this example, a GET request sends to the `https://api.example.com/data `endpoint and extract the response body as a string using the `bodyAsString` method.

**Example: Making a Simple HTTP Request**

```scala mdoc:silent 

import zio.http.model.headers.Headers
import zio.http.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object SimpleClient extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers.host("sports.api.decathlon.com")

  val program = for {
    res  <- Client.request(url, headers)
    data <- res.bodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.exitCode.provideCustomLayer(env)
}
```

This example demonstrates how to create a simple HTTP client using ZIO HTTP to make requests to an external API. It sends a request to the specified URL with custom headers, retrieves the response body as a string and prints it to the console.









