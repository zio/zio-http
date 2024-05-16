---
id: request-handling
title: "Request Handling "
---

# Request Handling

Request handling in ZIO HTTP involves extracting data from incoming HTTP requests and generating appropriate responses. This process is essential for building robust and scalable HTTP applications.


**The** HttpRequest **object** encapsulates all information about the incoming request, including:

- **Method**: The HTTP method used (GET, POST, PUT, etc.).
- **Path**: The requested URL path.
- **Headers**: Key-value pairs containing additional information from the client.
- **Body**: The request body containing data sent from the client (if applicable).

## Extracting Data from Requests

- ZIO HTTP provides utilities to extract various parts of an HTTP request:
  - **Path Parameters**: Extract dynamic segments from the URL path.
  - **Query Parameters**: Retrieve values from the query string.
  - **Headers**: Access HTTP headers for additional metadata.
  - **Request Body**: Read and parse the request body, which can be in different formats such as JSON, XML, or plain text.

### Generating Responses

- After processing the request, generate a response using ZIO HTTP's response utilities:
  - **Status Codes**: Set the appropriate HTTP status code (e.g., 200 OK, 404 Not Found).
  - **Response Body**: Include the response body, which can be in various formats such as JSON, XML, or plain text.
  - **Headers**: Add HTTP headers to the response for additional metadata.

## Simple Request Handling Example

```scala mdoc:silent
import zio._
import zio.http._

val app: HttpApp[Any, Nothing] =
  Http.collect[Request] {
    case req @ Method.GET -> !! / "greet" / name =>
      Response.text(s"Hello, $name!")
  }

val run = Server.start(8080, app)
```

In this example, we created a simple HTTP application that handles GET requests on the `/greet/{name}` path. The request handler extracts the `name` path parameter and generates a response with a greeting message.