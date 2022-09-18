---
sidebar_position: "6"
---
# Headers

**ZIO HTTP** provides support for all HTTP headers (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) along with  custom headers.

## Server-side

### Attaching Headers to `Response`
On the server-side, `ZIO-HTTP` is adding a collection of pre-defined headers to the response, according to the HTTP specification, additionally, users may add other headers, including custom headers.

There are multiple ways to attach headers to a response:
- Using `addHeaders` helper on response.
    ```scala
    val res = Response.ok.addHeader("content-length", "0")
    ```

- Through `Response` constructors.
    ```scala
    val res = Response(
           status = Status.OK,
           // Setting response header 
           headers = Headers.contentLength(0L),
           body = Body.empty
    ```
- Using `Middlewares`.
    ```scala
    val app = Http.ok @@ Middleware.addHeader("content-length", "0")
    ```

### Reading Headers from `Request`

On the Server-side you can read Request headers as given below

```scala
 case req @ Method.GET -> !! / "streamOrNot" =>
      req.getHeaders
```

<details>
<summary><b>Detailed examples </b></summary>


- Example below shows how the Headers could be added to a response by using `Response` constructors and how a custom header is added to `Response` through `addHeader`:

  ```scala
  import zio.http._
  import zio.http.Server
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
            body = Body.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
          )
        else { 
          // Adding a custom header to Response
          Response(status = Status.ACCEPTED, body = Body.fromChunk(message)).addHeader("X-MY-HEADER", "test")
        }
    }
  }
  
  ```
- The following example shows how Headers could be added to `Response` in the `Middleware` implementation:

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

- More examples:
  - [BasicAuth](https://github.com/zio/zio-http/blob/main/example/src/main/scala/BasicAuth.scala)
  - [Authentication](https://github.com/zio/zio-http/blob/main/example/src/main/scala/Authentication.scala)

</details>

## Client-side

### Adding headers to `Request` 

ZIO-HTTP provides a simple way to add headers to a client `Request`. 

```scala
val headers = Headers.host("sports.api.decathlon.com").withAccept(HeaderValues.applicationJson)
val response = Client.request(url, headers)
```

### Reading headers from `Response`

```scala
val responseHeaders: Task[Headers] =  Client.request(url).map(_.headers)
```

<details>
<summary><b>Detailed examples</b> </summary>

- The sample below shows how a header could be added to a client request:

    ```scala
    import zio.http._
    import zio.http.service._
    import zio._
    
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
            res.bodyAsString
          else
            res.bodyAsString
        _    <- console.putStrLn { data }
      } yield ()
    
      override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)
    
    }
    ```

</details>

## Headers DSL

Headers DSL provides plenty of powerful operators that can be used to add, remove, modify and verify headers. Headers APIs could be used on client, server, and middleware.

`zio.http.model.headers.Headers` - represents an immutable collection of headers i.e. essentially a `Chunk[(String, String)]`.

`zio.http.HeaderNames`           - commonly used header names.

`zio.http.HeaderValues`          - commonly used header values

`Headers` have following type of helpers
- Constructors -  Provides a list of helpful methods that can create `Headers`.

    ```scala
    import zio.http._
    
    // create a simple Accept header:
    val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)
    
    // create a basic authentication header:
    val basicAuthHeader: Headers = Headers.basicAuthorizationHeader("username", "password")
    ```

- Getters - Provides a list of operators that parse and extract data from the `Headers`.

    ```scala
    import zio.http._
    
    // retrieving the value of Accept header value:
    val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)
    val acceptHeaderValue: Option[CharSequence] = acceptHeader.getAccept
    
    
    // retrieving a bearer token from Authorization header:
    val authorizationHeader: Headers                   = Headers.authorization("Bearer test")
    val authorizationHeaderValue: Option[String]       = authorizationHeader.getBearerToken
    ```

- Modifiers - Provides a list of operators that modify the current `Headers`. Once modified, a new instance of the same type is returned.

    ```scala
    import zio.http._
    
    // add Accept header:
    val headers = Headers.empty
    val updatedHeadersList: Headers = headers.addHeaders(Headers.accept(HeaderValues.applicationJson))
    
    // or if you prefer the builder pattern:
    
    // add Host header:
    val moreHeaders: Headers        = headers.withHost("zio-http.dream11.com")
    
    ```

- Checks - Provides a list of operators that checks if the `Headers` meet the give constraints.

    ```scala
    val contentTypeHeader: Headers = Headers.contentType(HeaderValues.applicationJson)
    val isHeaderPresent: Boolean   = contentTypeHeader.hasHeader(HeaderNames.contentType) 
    val isJsonContentType: Boolean = contentTypeHeader.hasJsonContentType
    
    
    ```
