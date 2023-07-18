---
id: headers
title: Headers
---

**ZIO HTTP** provides support for all HTTP headers (as defined
in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) along with custom headers.

## Server-side

### Attaching Headers to `Response`

On the server-side, `ZIO-HTTP` is adding a collection of pre-defined headers to the response, according to the HTTP
specification, additionally, users may add other headers, including custom headers.

There are multiple ways to attach headers to a response:

Using `addHeaders` helper on response:
- 

```scala mdoc
import zio._
import zio.http._

Response.ok.addHeader(Header.ContentLength(0L))
```

Through `Response` constructors:

```scala mdoc
Response(
  status = Status.Ok,
  // Setting response header 
  headers = Headers(Header.ContentLength(0L)),
  body = Body.empty
)
```

Using `Middlewares`:

```scala mdoc
import Middleware.addHeader

Routes(Method.GET / "hello" -> Handler.ok) @@ addHeader(Header.ContentLength(0L))
```

### Reading Headers from `Request`

On the Server-side you can read Request headers as given below

```scala mdoc
Routes(
  Method.GET / "streamOrNot" -> handler { (req: Request) =>
    Response.text(req.headers.map(_.toString).mkString("\n"))
  }
)
```

<details>
<summary><b>Detailed examples </b></summary>

Example below shows how the Headers could be added to a response by using `Response` constructors and how a custom
header is added to `Response` through `addHeader`:

```scala mdoc:silent
import zio._
import zio.http._
import zio.stream._

object SimpleResponseDispatcher extends ZIOAppDefault {
  override def run =
  // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
    Server.serve(app).provide(Server.default)

  // Create a message as a Chunk[Byte]
  val message = Chunk.fromArray("Hello world !\r\n".getBytes(Charsets.Http))
  // Use `Http.collect` to match on route
  val app: HttpApp[Any] =
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
              // Setting response header 
              headers = Headers(Header.ContentLength(message.length.toLong)), // adding CONTENT-LENGTH header
              body = Body.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
            )
          else {
            // Adding a custom header to Response
            Response(status = Status.Accepted, body = Body.fromChunk(message)).addHeader("X-MY-HEADER", "test")
          }
        }
    ).ignore.toHttpApp
}

```
More examples:

- [Middleware](https://github.com/zio/zio-http/blob/main/example/src/main/scala/Middleware.scala)

</details>

## Client-side

### Adding headers to `Request`

ZIO-HTTP provides a simple way to add headers to a client `Request`.

```scala mdoc:silent
val headers = Headers(Header.Host("sports.api.decathlon.com"), Header.Accept(MediaType.application.json))
Client.request(Request.get("http://sports.api.decathlon.com/test").addHeaders(headers))
```

### Reading headers from `Response`

```scala mdoc:silent
Client.request(Request.get("http://sports.api.decathlon.com/test")).map(_.headers)
```

<details>
<summary><b>Detailed examples</b> </summary>

- The sample below shows how a header could be added to a client request:

```scala mdoc:silent
import zio._
import zio.http._

object SimpleClientJson extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"
  // Construct headers
  val headers = Headers(Header.Host("sports.api.decathlon.com"), Header.Accept(MediaType.application.json))

  val program = for {
    // Pass headers to request
    res <- Client.request(Request.get(url).addHeaders(headers))
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
    program.provide(Client.default, Scope.default)

}
```

</details>

## Headers DSL

Headers DSL provides plenty of powerful operators that can be used to add, remove, modify and verify headers. Headers
APIs could be used on client, server, and middleware.

`zio.http.Headers` - represents an immutable collection of headers
`zio.http.Header`  - a collection of all the standard headers

`Headers` have following type of helpers

Constructors - Provides a list of helpful methods that can create `Headers`.

```scala mdoc
// create a simple Accept header:
Headers(Header.Accept(MediaType.application.json))

// create a basic authentication header:
Headers(Header.Authorization.Basic("username", "password"))
```

Getters - Provides a list of operators that parse and extract data from the `Headers`.

```scala mdoc

// retrieving the value of Accept header value:
val acceptHeader: Headers = Headers(Header.Accept(MediaType.application.json))
val acceptHeaderValue: Option[CharSequence] = acceptHeader.header(Header.Accept).map(_.renderedValue)


// retrieving a bearer token from Authorization header:
val authorizationHeader: Headers = Headers(Header.Authorization.Bearer("test"))
val authorizationHeaderValue: Option[String] = acceptHeader.header(Header.Authorization).map(_.renderedValue)
```

Modifiers - Provides a list of operators that modify the current `Headers`. Once modified, a new instance of the same
type is returned.

```scala mdoc
// add Accept header:
Headers.empty.addHeader(Header.Accept(MediaType.application.json))
```

Checks - Provides a list of operators that checks if the `Headers` meet the give constraints.

```scala mdoc
val contentTypeHeader: Headers = Headers(Header.ContentType(MediaType.application.json))
val isHeaderPresent: Boolean   = contentTypeHeader.hasHeader(Header.ContentType) 
val isJsonContentType: Boolean = contentTypeHeader.hasJsonContentType
```
