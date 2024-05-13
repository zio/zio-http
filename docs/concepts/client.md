---
id: client
title: "Client"
---

# Client

ZIO HTTP empowers us to interact with remote HTTP servers by sending requests and handling responses. This enables us to build robust and scalable HTTP clients using ZIO's functional programming concepts.

## Making HTTP Requests

Once you have an HTTP client, you can use it to make various types of requests (GET, POST, PUT, DELETE, etc.) to external services. ZIO HTTP provides methods for constructing and sending requests.

```scala mdoc:silent 

import zio.http._
import zio._

val url = URL.decode("https://api.example.com/data")

val request = Request.get(url) 
```
In this example,created a simple GET request to the `https://api.example.com/data` endpoint and send it using the HTTP client. The send method returns a Response representing the server's response to the request.

## Handling Responses:

After sending a request, you can handle the response returned by the server. ZIO HTTP provides various methods for processing response data, such as reading headers, accessing the body and handling status codes.

```scala mdoc:silent 

import zio.http._
import zio._

val request = Request.get("https://api.example.com/data")

val responseProcessing: ZIO[Client & Scope, Throwable, String] =
  ZClient.default 
    .send(request) 
    .flatMap(response => response.body.asString 
    )
```

In this example, a GET request sends to the `https://api.example.com/data `endpoint and extract the response body as a string using the `body.asString` method.

**Simple Client Example**

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/SimpleClient.scala")
```

This example demonstrates how to create a simple HTTP client using ZIO HTTP to make requests to an external API. It sends a request to the specified URL with custom headers, retrieves the response body as a string and prints it to the console.









