# Headers API

Headers API provides a ton of powerful operators that can be used to add, remove and modify headers. 
Same Headers API could be used on both client, server and middleware.

`zhttp.http.Headers`      - represents an immutable collection of headers i.e. essentially a `Chunk[(String, String)]`.

`zhttp.http.HeaderNames`  - commonly used header names.

`zhttp.http.HeaderValues` - commonly used header values

## Client API

The following example shows how you can add headers on the client request and how to check presence of header in client response.
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

## Server API

The following example shows how to use Headers API on the server side. 

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
          headers = Headers.contentLength(message.length.toLong),
          data = HttpData.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
        )
      else Response(status = Status.ACCEPTED, data = HttpData.fromChunk(message))
  }
}

```

## Middleware API

Middleware Service allows access of both Request and Response headers. It provides:

`zhttp.http.Middleware.addHeader` - to add a custom header in response (for example, in case of basic authentication when you have to provide the  `WWW_AUTHENTICATE` header in case the authentication fail.)

`zhttp.http.Middleware.addHeaders` - to add one or more headers in response.

Example: 
- [BasicAuth](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/BasicAuth.scala)
- [Authentication](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/Authentication.scala)

## More examples

- Constructors:

```scala
import zhttp.http._

// create a simple Accept header:
val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)

// create a basic authentication header:
val basicAuthHeader: Headers = Headers.basicAuthorizationHeader("username", "password")
```

- Getters:

```scala
import zhttp.http._

// retrieving the value of Accept header value:
val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)
val acceptHeaderValue: Option[CharSequence] = acceptHeader.getAccept


// retrieving a bearer token from Authorization header:
val authorizationHeader: Headers                   = Headers.authorization("Bearer test")
val authorizationHeaderValue: Option[String]       = authorizationHeader.getBearerToken
```

- Modifiers:

```scala
import zhttp.http._

// add Accept header:
val headers = Headers.empty
val updatedHeadersList: Headers = headers.addHeaders(Headers.accept(HeaderValues.applicationJson))

// or if you prefer the builder pattern:

// add Host header:
val moreHeaders: Headers        = headers.withHost("zio-http.dream11.com")

```

- Checks:

```scala
import com.sun.net.httpserver.Headers

// check if Accept header is present
val contentTypeHeader: Headers = Headers.contentType(HeaderValues.applicationJson)
val isHeaderPresent: Boolean   = contentTypeHeader.hasHeader(HeaderNames.contentType)

val isJsonContentType: Boolean = contentTypeHeader.hasJsonContentType


```
