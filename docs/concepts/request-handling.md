---
id: request-handling
title: "Request Handling "
---

# Request Handling

Handling incoming HTTP requests is a fundamental aspect of development with ZIO HTTP. This section covers the process of extracting data from requests, processing it, and generating appropriate responses.

**The** HttpRequest **object** encapsulates all information about the incoming request, including:

- **Method**: The HTTP method used (GET, POST, PUT, etc.).
- **Path**: The requested URL path.
- **Headers**: Key-value pairs containing additional information from the client.
- **Body**: The request body containing data sent from the client (if applicable).

## Extracting Data from Requests

ZIO HTTP provides a flexible and type-safe mechanism for extracting data from incoming HTTP requests. This involves pattern matching on the request attributes to access specific information.

```scala
import zio.http._

val app = Http.collectZIO[Request] {
    case req @ Method.GET -> !! / "fruits" / "a"  =>
      UIO(Response.text("URL:" + req.url.path.asString + " Headers: " + req.getHeaders))
    case req @ Method.POST -> !! / "fruits" / "a" =>
      req.bodyAsString.map(Response.text(_))
}
```

In this example:

- For a GET request to `/fruits/a`, the server responds with the URL path and headers.
- For a POST request to `/fruits/a`, the server responds with the request body as text.

## Processing Requests

Once data is extracted from the request, it can be processed as needed. This may involve performing computations, accessing databases or external services, or applying business logic to generate a response

```scala
import zio.http._

val app = Http.collectM[Request] {
  case Method.GET -> Root / "square" / int(num) =>
    UIO(Response.text(s"Square of $num is ${num * num}"))
}
```
In this example, the server calculates the square of an integer provided in the request path and responds with the result.

## Generating Responses

After processing the request data, a response is generated based on the desired outcome. ZIO HTTP provides constructors for creating various types of responses, including text, JSON, HTML, and binary data.

```scala
val app = Http.collectM[Request] {
  case Method.GET -> Root / "json" =>
    UIO(Response.jsonString("""{"message": "Hello, JSON!"}"""))
}
```
In this example, the server responds with a JSON message when receiving a GET request to `/json`.