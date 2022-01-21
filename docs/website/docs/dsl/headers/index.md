# Headers

ZIO HTTP provides support for all HTTP (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) headers and custom headers are supported too.

## Server-side

### Attaching Headers to `Response`
On the server-side, `ZIO-HTTP` is adding a collection of pre-defined headers to any response, according to the HTTP specification, but on top of them, users of the library may add other headers, including custom headers.

There are multiple ways to attach headers to a response:
- Using `addHeaders` helper on response.
- Through `Response` constructors.
- Using `Middlewares`.
<details>
<summary>Example below shows how the Headers could be added to a response by using `Response` constructors and how a custom header is added to `Response` through `addHeader` API call: </summary>
<p>

```scala
import zhttp.http._
import zhttp.service.Server
import zio.{App, Chunk, ExitCode, URIO}
import zio.stream.ZStream

object SimpleResponseDispatcher extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
    Server.start(8090, app.silent).exitCode
  }

  // Create a message as a Chunk[Byte]
  val message                    = Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET))
  // Use `Http.collect` to match on route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {

    // Simple (non-stream) based route
    case Method.GET -> !! / "health" => Response.ok

    // From Request(req), the headers are accessible.
    case req @ Method.GET -> !! / "streamOrNot" =>
      // Checking if client is able to handle streaming response
      val acceptsStreaming: Boolean = req.hasHeader(HeaderNames.accept, HeaderValues.applicationOctetStream)
      if (acceptsStreaming)
        Response(
          status = Status.OK,
          // Setting response header 
          headers = Headers.contentLength(message.length.toLong), // adding CONTENT-LENGTH header
          data = HttpData.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
        )
      else { 
        // Adding a custom header to Response
        Response(status = Status.ACCEPTED, data = HttpData.fromChunk(message)).addHeader("X-MY-HEADER", "test")
      }
  }
}

```

The following example shows how Headers could be added to `Response` in the `Middleware` implementation: 

```scala

  /**
   * Creates an authentication middleware that only allows authenticated requests to be passed on to the app.
   */
  final def customAuth(
    verify: Headers => Boolean,
    responseHeaders: Headers = Headers.empty,
  ): HttpMiddleware[Any, Nothing] =
    Middleware.ifThenElse[Request](req => verify(req.getHeaders))(
      _ => Middleware.identity,
      _ => Middleware.fromHttp(Http.status(Status.FORBIDDEN).addHeaders(responseHeaders)),
    )

```

More examples:
- [BasicAuth](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/BasicAuth.scala)
- [Authentication](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/Authentication.scala)
</p>
</details>

### Reading Headers from `Request`

On the Server-side you can read Request headers. The example below shows how the request headers could be read. In the example, the ACCEPT request header is read/checked to see if the octet-streaming response is acceptable for the client.

```scala
...
 case req @ Method.GET -> !! / "streamOrNot" =>
      // Checking if client is able to handle streaming response
      val acceptsStreaming: Boolean = req.hasHeader(HeaderNames.accept, HeaderValues.applicationOctetStream)
```

## Client-Side

### Adding headers to `Request` 

ZIO-HTTP provides a simple way to add headers to a client `Request`. 
<details>
<summary>For example, in most cases, a client request should provide the Host header. The sample below shows how a header could be added to a client request:</summary>
<p>

```scala
import zhttp.http.{HeaderNames, HeaderValues, Headers}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO, console}

object SimpleClientJson extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  // Construct headers
  val headers = Headers.host("sports.api.decathlon.com").withAccept(HeaderValues.applicationJson) 

  val program = for {
    // Pass headers to request
    res  <- Client.request(url, headers)
    // List all response headers
    _    <- console.putStrLn(res.headers.toList.mkString("\n"))
    data <-
      // Check if response contains a specified header with a specified value.
      if (res.hasHeader(HeaderNames.contentType, HeaderValues.applicationJson))
        res.getBodyAsString
      else
        res.getBodyAsString
    _    <- console.putStrLn { data }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
```
</p>
</details>

### Reading headers from `Response`

```scala
val responseHeaders: Task[Headers] =  Client.request(url).map(_.headers)
```


## Headers DSL

Headers DSL provides plenty of powerful operators that can be used to add, remove and modify headers. Same Headers API could be used on both client, server, and middleware.

`zhttp.http.Headers`      - represents an immutable collection of headers i.e. essentially a `Chunk[(String, String)]`.

`zhttp.http.HeaderNames`  - commonly used header names.

`zhttp.http.HeaderValues` - commonly used header values

- Constructors - provides predefined constructors for most frequent used headers.

```scala
import zhttp.http._

// create a simple Accept header:
val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)

// create a basic authentication header:
val basicAuthHeader: Headers = Headers.basicAuthorizationHeader("username", "password")
```

- Getters - provides predefined getters for most frequent used headers.

```scala
import zhttp.http._

// retrieving the value of Accept header value:
val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)
val acceptHeaderValue: Option[CharSequence] = acceptHeader.getAccept


// retrieving a bearer token from Authorization header:
val authorizationHeader: Headers                   = Headers.authorization("Bearer test")
val authorizationHeaderValue: Option[String]       = authorizationHeader.getBearerToken
```

- Modifiers - provides `builder-pattern` oriented operations for most frequent used headers.

```scala
import zhttp.http._

// add Accept header:
val headers = Headers.empty
val updatedHeadersList: Headers = headers.addHeaders(Headers.accept(HeaderValues.applicationJson))

// or if you prefer the builder pattern:

// add Host header:
val moreHeaders: Headers        = headers.withHost("zio-http.dream11.com")

```

- Checks - provides operators that check if the Headers meet the given constraints.

```scala
import com.sun.net.httpserver.Headers

// check if Accept header is present
val contentTypeHeader: Headers = Headers.contentType(HeaderValues.applicationJson)
val isHeaderPresent: Boolean   = contentTypeHeader.hasHeader(HeaderNames.contentType)

val isJsonContentType: Boolean = contentTypeHeader.hasJsonContentType


```
