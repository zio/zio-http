---
id: headers
title: Headers
---

**ZIO HTTP** provides support for all HTTP headers (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616)) along with custom headers.

In ZIO HTTP we have two related types of headers:

- `Header` represents a single HTTP header
- `Headers` represents an immutable collection of headers

## Header

The `Header` trait outlines the fundamental interface for all HTTP headers. We can think of it as a type-safe representation of an HTTP header, consisting of a key-value pair where the key represents the header name and the value represents the header value.

In the companion object of `Header`, we have a collection of predefined headers. They are grouped into sub-objects based on their category, such as `zio.http.Header.Authorization`, `zio.http.Header.CacheControl`, etc. All the headers are subtypes of `zio.http.Header` which is a sealed trait.

By calling `headerName` and `renderedValue` on a header instance, we can access the header name and value, respectively.

## Headers

`Headers` is a collection of `Header` instances which is used to represent the headers of an HTTP message:

```scala mdoc
import zio.http._

val headers1 = Headers(Header.Accept(MediaType.application.json))

val headers2 = Headers(
    Header.Accept(MediaType.application.json),
    Header.Authorization.Basic("username", "password")
  )
```

We can use raw strings to create headers:

```scala mdoc
import zio.http._
// Creating headers from key-value pair
val headers4 = Headers("Accept", "application/json")

// Creating headers from tuple of key-value pair
val headers3 = Headers("Accept" -> "application/json")
```

## Headers Operations

Headers DSL provides plenty of powerful operators that can be used to add, remove, modify, and verify headers. There are several operations that can be performed on any instance of `Headers`, `Request`, and `Response`.

### Getting Headers

To get headers from a request or response, we can use the `header` method:

```scala mdoc:invisible
val response = Response.ok.addHeader("Content-Type", "application/json; charset=utf-8")

val request = Request.get("/users").addHeader(Header.Accept(MediaType.application.`json`))
```

```scala mdoc:compile-only
response.header(Header.ContentType)

request.header(Header.Authorization)
```

List of methods available to get headers:

| Method                                 | Description                                                                                   | Return Type                                      |
|----------------------------------------|-----------------------------------------------------------------------------------------------|--------------------------------------------------|
| `header(headerType: HeaderType)`       | Gets a header or returns `None` if not present or unparsable.                                 | `Option[headerType.HeaderValue]`                 |
| `headers(headerType: HeaderType)`      | Gets multiple headers of the specified type.                                                  | `Chunk[headerType.HeaderValue]`                  |
| `headerOrFail(headerType: HeaderType)` | Gets a header, returning `None` if absent, or an `Either` with parsing error or parsed value. | `Option[Either[String, headerType.HeaderValue]]` |
| `headers`                              | Returns all headers.                                                                          | `Headers`                                        |
| `rawHeader(name: CharSequence)`        | Gets the raw unparsed value of a header by name.                                              | `Option[String]`                                 |
| `rawHeader(headerType: HeaderType)`    | Gets the raw unparsed value of a header by type.                                              | `Option[String]`                                 |

### Modifying Headers

There are several methods available to modify headers. Once modified, a new instance of the same type is returned:

```scala mdoc:compile-only
Request.get("/users").addHeader(Header.Accept(MediaType.application.`json`))

Response.ok.addHeaders(
  Headers(
    Header.ContentType(MediaType.application.json),
    Header.AccessControlAllowOrigin.All
  )
)

Headers.empty.addHeader(Header.Accept(MediaType.application.json))
```

Here are the methods available to modify headers:

| Method          | Description                                                   |
|-----------------|---------------------------------------------------------------|
| `addHeader`     | Adds a header or a header with the given name and value.      |
| `addHeaders`    | Adds multiple headers.                                        |
| `removeHeader`  | Removes a specified header.                                   |
| `removeHeaders` | Removes multiple specified headers.                           |
| `setHeaders`    | Sets the headers to the provided headers.                     |
| `updateHeaders` | Updates the current headers using a provided update function. |

### Checking for a Certain Condition

There are methods available that enable us to verify whether the headers meet specific criteria:

```scala mdoc:compile-only
response.hasContentType(MediaType.application.json.fullType)

request.hasHeader(Header.Accept)

val contentTypeHeader: Headers = Headers(Header.ContentType(MediaType.application.json))

contentTypeHeader.hasHeader(Header.ContentType) 

contentTypeHeader.hasJsonContentType
```

There are several such methods available to check if the headers meet certain conditions:

| `Method`                              | Description                                                    |
|---------------------------------------|----------------------------------------------------------------|
| `hasContentType(value: CharSequence)` | Checks if the headers have the given content type.             |
| `hasFormUrlencodedContentType`        | Checks if the headers have a form-urlencoded content type.     |
| `hasFormMultipartContentType`         | Checks if the headers have a multipart/form-data content type. |
| `hasHeader(name: CharSequence)`       | Checks if the headers contain a header with the given name.    |
| `hasHeader(headerType: HeaderType)`   | Checks if the headers contain a header of the given type.      |
| `hasHeader(header: Header)`           | Checks if the headers contain a specific header.               |
| `hasJsonContentType`                  | Checks if the headers have a JSON content type.                |
| `hasMediaType(mediaType: MediaType)`  | Checks if the headers have the specified media type.           |
| `hasTextPlainContentType`             | Checks if the headers have a text/plain content type.          |
| `hasXhtmlXmlContentType`              | Checks if the headers have an XHTML/XML content type.          |
| `hasXmlContentType`                   | Checks if the headers have an XML content type.                |

## Server-side

### Attaching Headers to Response

On the server-side, ZIO HTTP is adding a collection of pre-defined headers to the response, according to the HTTP specification, additionally, users may add other headers, including custom headers.

There are multiple ways to attach headers to a response:

#### Using `addHeaders` Helper on Response

```scala mdoc
import zio._
import zio.http._

Response.ok.addHeader(Header.ContentLength(0L))
```

#### Through Response Constructors

```scala mdoc
Response(
  status = Status.Ok,
  // Setting response header 
  headers = Headers(Header.ContentLength(0L)),
  body = Body.empty
)
```

#### Using Middlewares

```scala mdoc
import Middleware.addHeader

Routes(Method.GET / "hello" -> Handler.ok) @@ addHeader(Header.ContentLength(0L))
```

### Reading Headers from Request

On the Server-side you can read Request headers as given below:

```scala mdoc
Routes(
  Method.GET / "streamOrNot" -> handler { (req: Request) =>
    Response.text(req.headers.map(_.toString).mkString("\n"))
  }
)
```

<details>
<summary><b>Detailed Example</b></summary>

Example below shows how the Headers could be added to a response by using `Response` constructors and how a custom header is added to `Response` through `addHeader`:

```scala mdoc:silent
import zio._
import zio.http._
import zio.stream._

object SimpleResponseDispatcher extends ZIOAppDefault {
  override def run =
    // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
    Server.serve(routes).provide(Server.default)

  // Create a message as a Chunk[Byte]
  val message = Chunk.fromArray("Hello world !\r\n".getBytes(Charsets.Http))
  val routes: Routes[Any, Response] =
    Routes(
      // Simple (non-stream) based route
      Method.GET / "health" -> handler(Response.ok),

      // From Request(req), the headers are accessible.
      Method.GET / "streamOrNot" ->
        handler { (req: Request) =>
          // Checking if client is able to handle streaming response
          val acceptsStreaming: Boolean = req.header(Header.Accept).exists(_.mimeTypes.contains(Header.Accept.MediaTypeWithQFactor(MediaType.application.`octet-stream`, None)))
          if (acceptsStreaming)
            Response(
              status = Status.Ok,
              body = Body.fromStream(ZStream.fromChunk(message), message.length.toLong), // Encoding content using a ZStream
              )
          else {
            // Adding a custom header to Response
            Response(status = Status.Accepted, body = Body.fromChunk(message)).addHeader("X-MY-HEADER", "test")
          }
        }
      ).sandbox
}
```

</details>

## Client-side

### Adding Headers to Request

ZIO HTTP provides a simple way to add headers to a client `Request`.

```scala mdoc:silent
val headers = Headers(Header.Host("jsonplaceholder.typicode.com"), Header.Accept(MediaType.application.json))
Client.request(Request.get("https://jsonplaceholder.typicode.com/todos").addHeaders(headers))
```

### Reading Headers from Response

```scala mdoc:silent
Client.request(Request.get("https://jsonplaceholder.typicode.com/todos")).map(_.headers)
```

<details>
<summary><b>Detailed Example</b> </summary>

The sample below shows how a header could be added to a client request:

```scala mdoc:silent
import zio._
import zio.http._

object SimpleClientJson extends ZIOAppDefault {
  val url = "https://jsonplaceholder.typicode.com/todos"
  // Construct headers
  val headers = Headers(Header.Host("jsonplaceholder.typicode.com"), Header.Accept(MediaType.application.json))

  val program = for {
    // Pass headers to request
    res <- Client.batched(Request.get(url).addHeaders(headers))
    // List all response headers
    _ <- Console.printLine(res.headers.toList.mkString("\n"))
    data <-
      // Check if response contains a specified header with a specified value.
      if (res.header(Header.ContentType).exists(_.mediaType == MediaType.application.json))
        res.body.asString
      else
        res.body.asString
    _ <- Console.printLine(data)
  } yield ()

  override def run =
    program.provide(Client.default)

}
```

</details>
