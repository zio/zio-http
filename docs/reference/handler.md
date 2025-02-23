---
id: handler
title: Request Handler
---

A `Handler` is responsible for processing the matched incoming request and generating an appropriate response. It is a function that takes a `Request` and produces a `Response`. Thus, it is a crucial component of the ZIO HTTP that determines how the server should respond to a request matched by the corresponding `RoutePattern`.

:::note
In ZIO HTTP, each `Route` consists of a `RoutePattern` and a `Handler`. The `RoutePattern` is responsible for matching the **method** and **path** of the incoming request, while the `Handler` specifies the corresponding action to be taken when the request is matched.
:::note

## Definition

The `Hander` trait is defined as follows:

```scala
sealed trait Handler[-R, +Err, -In, +Out] {
  def apply(in: In): ZIO[R, Err, Out]
}
```

It has four type parameters. The first two parameters `R` and `Err` are the environment and error type of the underlying effect that the handler represents. The third and fourth parameters `In` and `Out` are the input and output types of the handler.

If the input type of the handler is `Request` and the output type is `Response`, we call that handler a **request handler**:

```scala
type RequestHandler[-R, +Err] = Handler[R, Err, Request, Response]
```

## Creating a Handler

The `Handler` trait comes with a companion object that has different methods to create handlers for various needs.

Additionally, there's a smart constructor called `handler` in the `zio.http` package. It automatically picks the right handler constructor based on the input type. Usually, using `handler` is enough, but if we need more control and specificity, we can also use the methods in the `Handler` companion object.

Let's look at some examples of creating handlers, using the `handler` smart constructor:

```scala mdoc
import zio._
import zio.http._

Routes(

  // 1. A simple handler that returns a "Hello, World!" response
  Method.GET / "hello" -> 
    handler(Response.text("Hello, World!")),
  
  // 2. A handler that echoes the request body
  Method.POST / "echo" ->
    handler { (req: Request) => req.body.asString(Charsets.Utf8).map(Response.text(_)).orDie },

  // 3. A handler that generates a random UUID
  Method.GET / "uuid" -> 
    handler(Random.nextUUID.map(u => Response.text(u.toString))),

  // 4. A handler that takes the name from the path and returns a greeting message
  Method.GET / "name" / string("name") -> 
    handler{ (name: String, _: Request) => Response.text(s"Hello, $name!") },

  // 5. A handler that takes the name and age from the path and returns birthday greetings
  Method.GET / "name" / string("name") / "age" / int("age") ->
    handler{ (name: String, age: Int, _: Request) => Response.text(s"Happy $age-th birthday, $name!") }

)
```

:::note
Please be aware that this page primarily concentrates on the `Handler` data type and its constructors. However, to provide a more comprehensive understanding within the context of routes, we also integrate examples with the `Routes` and `Method` data types. Detailed exploration of the `Routes` and `Method` data types is discussed in a [separate section](./routing/routes.md).
:::

As we can see, the `handler` constructor is quite versatile and can be used to create handlers for different use cases. It automatically infers proper handler constructors based on the input we pass to it.

1. The first example shows a simple handler that only returns a "Hello, World!" response. It doesn't need any input, so we can directly pass the `Response` to the `handler` constructor.
2. The second example shows a handler that echoes the request body. Since it needs the request body, we pass a function that takes a `Request` and returns a `Response`.

   :::note
   Please note that this handler employs the `orDie` method to transform any failures in the effect into defects. In real-world applications, it's advisable to handle failures more gracefully, such as returning a descriptive error message in the response body. This approach provides clients with a clear understanding of what went wrong. We will delve into error handling in a separate section.
   :::

3. The third example shows a handler that generates a random UUID. It doesn't need any input, but it requires an effect that produces a `UUID`. So, we pass a `ZIO` effect that generates a random `UUID` and returns a `Response`.
4. The fourth example shows a handler that takes the name from the path and returns a greeting message. It needs the name from the path, so we pass a function that takes a `String`, (and also the `Request` which we ignore it using `_`), and returns a `Response`. Please note that whenever we need to access path parameters, we need also to pass the `Request` as an argument to the handler function, even if we don't use it.
5. The fifth example is similar to the previous one, but it takes two path parameters.

## Handler Constructors

As mentioned earlier, it is advisable to use the `handler` smart constructor for convenience. However, in some cases, we might use lower-level handler constructors. Let's look at some of the most commonly used handlers:

### Handler.ok

Creates a `Handler` that always responds with a 200 status code.

```scala mdoc:silent
Handler.ok
```

### Succeed/Fail/Die

Like the `ZIO` effect, we can create handlers that `succeed`, `fail`, or `die` using the following constructors:

```scala mdoc
Handler.succeed(42)

Handler.fail(new Error("Server Error!"))

Handler.failCause(Cause.fail("Server Error!"))

Handler.die(new RuntimeException("Boom!"))

Handler.dieMessage("Boom!")
```

Please note that the second type parameter of `Handler` is the error type. The `succeed` handler doesn't have an error type (`Nothing`), the `fail` handler has an error type of the given error instance, and the `die` handler has no error type (`Nothing`) which means it converted the error into a defect.

:::info
ZIO boasts a robust error model, enabling us to manage errors in a type-safe and composable manner. If you're unfamiliar with ZIO, it's advisable to explore the [Error Management](https://zio.dev/reference/error-management/) section in the core ZIO documentation. This section provides insights into the principles of error handling within ZIO.
:::

### Importing non-ZIO Code 

Sometimes we need to import a non ZIO code to a `Handler`. The code may throw an exception and we want to capture all non-fatal exceptions while importing to the `Handler`, in such cases we can use the `Handler.attempt` constructor. It takes a thunk of type `Out` and returns a `Handler` that have `Throwable` as the error type and result type as `Out`:

```scala
object Handler {
  def attempt[Out](out: => Out): Handler[Any, Throwable, Any, Out] = ???
}
```

### From `Either` and `Exit`

If we have an `Either` or `Exit` which are the result of some computation, we can convert them to a `Handler` using the corresponding constructors:

```scala mdoc
import zio._
import zio.http._

Handler.fromExit(Exit.succeed(42))
Handler.fromExit(Exit.fail("failed!"))

Handler.fromEither(Right(42))
Handler.fromEither(Left("failed"))
```

### First Success

If we have a list of handlers and we want to run them in sequence until the first success, we can use the `firstSuccessOf` constructor:

```scala mdoc
Handler.firstSuccessOf(
  NonEmptyChunk(
    Handler.notFound("Requested resource not found in cache!"),
    Handler.succeed(Response.text("Requested resource found in database!")),
    Handler.succeed(Response.text("Requested resource found on the remote server!"))
  )
)
```

### From ZIO Effect

We can easily convert a `ZIO` effect to a `Handler` using the `fromZIO` constructor:

```scala mdoc
Handler.fromZIO(ZIO.succeed(42))

Handler.fromZIO(Random.nextUUID)
```

### From Response

To create a handler that always returns a specific response, we can use the `Handler.fromResponse` constructor, or if we have a `ZIO` effect that produces a response, we can use the `Handler.fromResponseZIO` constructor:

```scala mdoc
Handler.fromResponse(Response.text("Hello, World!"))

Handler.fromResponseZIO(Random.nextUUID.map(u => Response.text(u.toString)))
```

### From ZIO Stream

ZIO HTTP uses ZIO Streams to handle streaming data. Using `Handler.fromStream` and `Handler.fromStreamChunked` we can create handlers that produces a response from a ZIO Stream:

- **`Handler.fromStream`**- Takes a `ZStream` and the `contentLength`, and produces a `Handler` that returns a response with the given content length from the stream. It waits for the stream to complete before sending the response body. It has two variants, one for producing a response from a stream of `String` and the other one for a stream of `Byte`:

```scala
object Handler {
  def fromStream[R](
    stream: ZStream[R, Throwable, String],
    contentLength: Long, charset: Charset = Charsets.Http
  ): Handler[R, Throwable, Any, Response] = ???

  def fromStream[R](
    stream: ZStream[R, Throwable, Byte],
    contentLength: Long
  ): Handler[R, Throwable, Any, Response] = ???
}
```

Let's try an example:

```scala mdoc:compile-only
import zio.http._
import zio.stream._

Routes(
  Method.GET / "stream" ->
    Handler
      .fromStream(
        stream = ZStream
          .iterate(0)(_ + 1)
          .intersperse("\n")
          .map(_.toString)
          .schedule(Schedule.fixed(1.second)),
        contentLength = 10,
      )
)
```

In this example, when the client sends a GET request to /stream, the server responds with a stream of numbers separated by new lines. The content length of the response is set to 10, leading to the connection closing after the client receives a content body of size 10.

- **`Handler.fromChunkedStream`**- Takes a `ZStream`, and produces a `Handler` that returns a chunked response from the stream. It sends the chunks as they are produced by the stream to the client. This is useful for streaming large files or when the content length of the stream is not known in advance. Like the `fromStream` constructor, it has two variants:

```scala
object Handler {
  def fromStreamChunked[R](
    stream: ZStream[R, Throwable, String],
    charset: Charset = Charsets.Http
  ): Handler[R, Throwable, Any, Response] = ???

  def fromStreamChunked[R](
    stream: ZStream[R, Throwable, Byte]
  ): Handler[R, Throwable, Any, Response] = ???
}
```

Now, let's try another example, this time using `fromStreamChunked`:

```scala mdoc:compile-only
import zio.http._
import zio.stream._

Routes(
  Method.GET / "stream" ->
    Handler
      .fromStreamChunked(
        ZStream
          .iterate(0)(_ + 1)
          .intersperse("\n")
          .map(_.toString)
          .schedule(Schedule.fixed(1.second)),
      ).orDie
)
```

In this example, when the client sends a GET request to `/stream`, the server responds with a stream of numbers separated by new lines. As the stream produces infinite numbers, the client receives the numbers as they are produced by the server.

### From HTML, Text, and Template

#### Creating a Plain Text Response

The `Handler.text` constructor takes a `String` and produces a `Handler` that returns a response with the given plain text content and the `Content-Type` header set to `text/plain`:

```scala mdoc:compile-only
Handler.text("Hello world!")
```

#### Creating an HTML Response

ZIO HTTP has a DSL for creating HTML responses. To use it, we need to import the `zio.http.template._` package. The `Handler.html` constructor takes an `Html` element and produces a `Handler` that returns a response with the given HTML content and the `Content-Type` header set to `text/html`:

```scala mdoc:compile-only
import zio.http._
import zio.stream._
import zio.http.template._

Routes(
  Method.GET / "html" ->
    Handler.html(

      html(
        // Support for child nodes
        head(
          title("ZIO HTTP"),
        ),
        body(
          div(
            // Support for css class names
            css := "container text-align-left",
            h1("Hello World"),
            ul(
              // Support for inline css
              styles := "list-style: none",
              li(
                // Support for attributes
                a(href := "/hello/world", "Hello World"),
              ),
              li(
                a(href := "/hello/world/again", "Hello World Again"),
              ),

              // Support for Seq of Html elements
              (2 to 10) map { i =>
                li(
                  a(href := s"/hello/world/i", s"Hello World $i"),
                )
              },
            ),
          ),
        ),
      )
    )
)
```

#### Creating an HTML Response Using Template

ZIP HTTP has a simple built-in template which is useful for creating simple HTML pages with minimal effort. Let's see an example:

```scala mdoc:compile-only
import zio.http._
import zio.http.template._

Routes(
  Method.GET / "hello" -> 
    Handler.template("Hello world!")(
      html(
        body(
          p("This is a simple HTML page.")
        )
      )
    )
)
```

Let's see what happens if the client requests the above route:

```curl
$> curl -i http://127.0.0.1:8080/hello
HTTP/1.1 200 OK
content-type: text/html
content-length: 352

<!DOCTYPE html><html><head><title>ZIO HTTP - Hello world!</title><style>
 body {
   font-family: monospace;
   font-size: 16px;
   background-color: #edede0;
 }
</style></head><body><div style="margin:auto;padding:2em 4em;max-width:80%"><h1>Hello world!</h1><html><body><p>This is a simple Hello, World! HTML page.</p></body></html></div></body></html>⏎
```

### Timeout Handler

The `Handler.timeout` takes a `Duration` and returns a `Handler` that times out after the given duration with `408` status code.

### Status Codes

ZIO HTTP provides a set of constructors for creating handlers that return responses with specific status codes. Here are some of the common ones:

| Handler                       | HTTP Status Code |
|-------------------------------|------------------|
| `Handler.ok`                  | 200              |
| `Handler.badRequest`          | 400              |
| `Handler.forbidden`           | 403              |
| `Handler.tooLarge`            | 413              |
| `Handler.notFound`            | 404              |
| `Handler.methodNotAllowed`    | 405              |
| `Handler.internalServerError` | 500              |

If we need to create a handler that returns a response with a specific status code other than the ones listed above, we can use the `Handler.status` constructor.

The `Handler.status` constructor creates a `Handler` that always responds with the same status code and empty data:

```scala mdoc:silent
Handler.status(Status.Ok)
```

### Handler.error

Creates a `Handler` that always fails with the given error.

```scala mdoc:silent
Handler.error(Status.Forbidden)
```

### Creating a Handler From Body

The `Handler.fromBody` constructor takes a `Body` and produces a `Handler` that always returns a response with the given body with a 200 status code.

### Creating a Bounded Body Consumer

The `Handler.asChunkBounded` constructor takes a `Request` and the maximum size of the body in bytes (`limit`), and produces a `Handler` that consumes the body of the request and returns a chunk of bytes. If the body size of the request exceeds the given limit, the handler throws an exception:

```scala mdoc:compile-only
import zio.http._
import zio.stream._
import zio.schema.codec.JsonCodec.zioJsonBinaryCodec

Routes(
  Method.POST / "bounded-body-consumer" ->
    handler { (request: Request) =>
      Handler
        .asChunkBounded(request, limit = 10)
        .map { x: Chunk[Byte] =>
          Response(body = Body.fromStream(ZStream.fromChunk(x)))
        }.orDie
    }.flatten
)
```

### From Function

To create a `Handler` using a pure function, use `Handler.fromFunction`. It takes a function from `In` to `Out` (`Int => Out`) and returns a `Handler` that takes `In` and returns `Out` (`Handler[Any, Nothing, In, Out]`).

The following example shows how to create a handler that takes an `Int` and `Request` from the input and returns a `Response`:

```scala mdoc
import zio.json._
import zio.http._

Routes(
  Method.GET / "users" / int("userId")  ->
    Handler.fromFunction[(Int, Request)] { case (userId: Int, request: Request) =>
      Response.json(
        Map(
          "user"          -> userId.toString,
          "correlationId" -> request.headers.get("X-Correlation-ID").get,
        ).toJsonPretty,
      )
    }
)
```

The `Handler.fromFunction` has some variants that are useful in different scenarios:

| Constructor                   | Input Function                   | Output                           |
|-------------------------------|----------------------------------|----------------------------------|
| `Handler.fromFunction`        | `In => Out`                      | `Handler[Any, Nothing, In, Out]` |
| `Handler.fromFunctionHandler` | `In => Handler[R, Err, In, Out]` | `Handler[R, Err, In, Out]`       |
| `Handler.fromFunctionExit`    | `In => Exit[Err, Out]`           | `Handler[Any, Err, In, Out]`     |
| `Handler.fromFunctionZIO`     | `In => ZIO[R, Err, Out]`         | `Handler[R, Err, In, Out]`       |

### From File

The `Handler.fromFile` and `Handler.fromFileZIO` constructors are used to create handlers that return a file from the server:

| Constructor           | Input Function            | Output                                 |
|-----------------------|---------------------------|----------------------------------------|
| `Handler.fromFile`    | `File`                    | `Handler[R, Throwable, Any, Response]` |
| `Handler.fromFileZIO` | `ZIO[R, Throwable, File]` | `Handler[R, Throwable, Any, Response]` |

Let's see an example:

```scala mdoc:compile-only
import zio.http._
import java.io.File

Routes(
  Method.GET / "video" -> 
    Handler.fromFile(new File("src/main/resources/TestVideoFile.mp4")),
  Method.GET / "text"  -> 
    Handler.fromFile(new File("src/main/resources/TestFile.txt")),
)
```

### Parameter Extractor

The `Handler.param` is a builder that takes a type parameter `A` and returns a `ParamExtractorBuilder[A]` which is used to extract a parameter from the input. It is useful when we have set of small handlers that are working with only part of the request, so we can extract the part that is required and pass it to the corresponding handler. All these handlers have unified input but may have different output types; this is where we can easily combine them using monadic composition.

Here is an example:

```scala mdoc:compile-only
import zio.http._

Routes(
  Method.GET / "static" / trailing -> handler {
    // Path extractor
    val pathExtractor: Handler[Any, Nothing, (Path, Request), Path] = 
      Handler.param[(Path, Request)](_._1)

    // Request extractor
    val requestExtractor: Handler[Any, Nothing, (Path, Request), Request] =
      Handler.param[(Path, Request)](_._2)

    def logRequest(request: Request): Handler[Any, Throwable, Request, Response] = ???
    def staticFileHandler(path: Path): Handler[Any, Throwable, Request, Response] = ???

    for {
      path    <- pathExtractor
      request <- requestExtractor
      _       <- logRequest(request).contramap[(Path,Request)](_._2)
      resp    <- staticFileHandler(path).contramap[(Path, Request)](_._2)
    } yield (resp)
  }.sandbox
)
```

### Websocket

The `Handler.webSocket` constructor takes a function of type `WebSocketChannel => ZIO[Env, Throwable, Any]` and returns a `Handler` that handles the WebSocket requests.

```scala
object Handler {
  final def webSocket[Env](
    f: WebSocketChannel => ZIO[Env, Throwable, Any],
  ): WebSocketApp[Env] = ???
}
```

The following example shows how to create an echo server using the `Handler.webSocket` constructor:

```scala mdoc:compile-only
import zio.http._
import zio.http.ChannelEvent._

Routes(
  Method.GET / "websocket" ->
    handler {
      Handler.webSocket { channel =>
        channel.receiveAll {
          case Read(WebSocketFrame.Text(text)) =>
            channel.send(Read(WebSocketFrame.Text(text)))
          case _                               => 
            ZIO.unit
        }
      }.toResponse
    }
)
```

:::note
To be able to create a complete route, we need to convert the `WebSocketApp` to a `Response` using the `toResponse` method.
:::

### Stack Trace

By using `Handler.stackTrace` we can create a `Handler` that captures the ZIO stack trace at the current point:

```scala
object Handler {
  def stackTrace(implicit trace: Trace): Handler[Any, Nothing, Any, StackTrace] =
    fromZIO(ZIO.stackTrace)
}
```

Let's try an example:

```scala mdoc:compile-only
import zio.http._

Routes(
  Method.GET / "stacktrace" ->
    handler {
      for {
        stack <- Handler.stackTrace
      } yield Response.text(stack.prettyPrint)
    }
)
```

:::note
Returning a full stack trace in the body of an HTTP response is generally not recommended for production environments. Stack traces can contain sensitive information about your application's internals, which could be exploited by attackers.

A better practice is to log the stack trace on the server side for debugging purposes and return a more user-friendly error message to the client. This approach provides clients with a clear understanding of what went wrong. We will delve into error handling in a separate section.
:::

## Handler Operators

Like`ZIO` data type, the `Handler` has various operators for handling errors, timing out, combining handlers, mapping etc.

### Handler Aspect

To attach a handler aspect to a handler, we use the `@@` operator. For instance, the following code shows an example where we attach a logging handler to the echo handler:

```scala mdoc:compile-only
import zio.http._

Routes(
  Method.GET / "echo" -> handler { req: Request =>
    Handler.fromBody(req.body)
  }.flatten @@ HandlerAspect.requestLogging()
)
```

This will log every request coming to these handlers. ZIO HTTP supports various `HandlerAspects` that you can learn about in the [Middleware](aop/middleware.md) section.

### Sandboxing Errors

The `Handler#sandbox` operator is a potentially time-saving solution for managing errors within an HTTP application. Its primary function is the elimination of errors by translating them into an error of type `Response`, allowing developers to transition into a controlled environment where errors are effectively mitigated:

```scala
sealed trait Handler[-R, +Err, -In, +Out] { self =>
  def sandbox: Handler[R, Response, In, Out]
}
```

This tool could serve as a shortcut for developers who wish to bypass the complication of error handling, enabling them to focus more on other aspects of their code.

Let's see an example:

```scala mdoc:compile-only
import zio.http._
import java.nio.file._

Routes(
  Method.GET / "file" ->
    Handler.fromFile(Paths.get("file.txt").toFile).sandbox,
)
```

In this example, the type of the handler before applying the `sandbox` operator is `Handler[Any, Throwable, Any, Response]`. After applying the `sandbox` operator, the type of the handler becomes `Handler[Any, Response, Any, Response]`.

Without the `sandbox` operator, the compiler would complain about the unhandled `Throwable` error.

By default, sandboxed errors will result in a `500 Internal Server Error` response without a body. If you want to have all information about the error in the response body you can use a different (`ErrorResponseConfig`)[response/response.md#failure-responses-with-details] like `ErrorResponseConfig.debug`:

```scala mdoc:compile-only
import zio.http._
import java.nio.file._

Routes(
   Method.GET / "file" ->
           Handler.fromFile(Paths.get("file.txt").toFile).sandbox,
   ) @@ ErrorResponseConfig.debug
```
### Converting a `Handler` to an `Routes`

The `Handler#toRoutes` operator, converts a handler to an `Routes` to be served by the `Server`. The following example, shows an HTTP application that serves a simple "Hello, World!" response for all types of incoming requests:

```scala mdoc:compile-only
import zio._
import zio.http._

object HelloWorldServer extends ZIOAppDefault {
  def run =
    Server
      .serve(Handler.fromResponse(Response.text("Hello, world!")).toRoutes)
      .provide(Server.default)
}
```

### Response Projections

The `Handler#header`, `Handler#headers`, `Handler#status`, and `Handler#body` operators are used to extract specific parts of the response from a handler's output:

```scala
trait Handler[-R, +Err, -In, +Out] {
  def header(headerType: HeaderType)(
    implicit ev: Out <:< Response
  ): Handler[R, Err, In, Option[headerType.HeaderValue]]
  def headers(implicit ev: Out <:< Response): Handler[R, Err, In, Headers]
  def status(implicit ev: Out <:< Response, trace: Trace): Handler[R, Err, In, Status]
  def body(implicit trace: Trace): Handler[R, Err, In, Body]
}
```

### Running Handler

We know that a handler is just a function that takes an input and returns an output:

```scala
trait Handler[-R, +Err, -In, +Out] {
  def apply(in: In): ZIO[R, Err, Out]
}
```

So, to run a handler, we just need to call the `apply` method with the required input, and it will return a `ZIO` effect. The `Handler#runZIO` is an alternative to the `Handler#apply` method.

In cases where the input type of handler is `Request`, we can use the `Handler#run` method:

```scala
trait Handler[-R, +Err, -In, +Out] {
  def run(
    method: Method = Method.GET,
    path: Path = Path.root,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  )(implicit ev: Request <:< In): ZIO[R, Err, Out]
}
```

### Mapping

Like `ZIO` data type, the `Handler` has various operators for mapping the input and output types, and error types:

| Operator              | Explanation                                                    | Variations                                                           |
|-----------------------|----------------------------------------------------------------|----------------------------------------------------------------------|
| `Handler#map*`        | Used to transform the output of a handler.                     | `map`, `mapError`, `mapErrorCause`, `mapZIO`, `mapErrorZIO`, `mapErrorCauseZIO` |
| `Handler#contramap*`  | Used to transform the input of a handler.                      | `contramap`, `contramapZIO` |
| `Handler#mapError*`   | Used to transform the error type of a handler.                 | `mapError`, `mapErrorCause`, `mapErrorZIO`, `mapErrorCauseZIO` |

:::note
If you're unfamiliar with these operators, it's recommended to explore the [core ZIO documentation](https://zio.dev/reference/) for a deeper understanding of their functionality.
:::

### Composing Handlers

1. **flatMap** — This function is utilized to compose two handlers in scenarios where we aim to employ monadic composition. It combines the output of the first handler with the input of the second handler. The `>>=` operator serves as an alias for `flatMap`.

2. **andThen** — Employed to sequentially compose a handler with another handler, without the need for monadic composition. Given two handlers, `h1: A => B` and `h2: B => C`, the resulting function `A => C` is achieved through `h1 andThen h2`. The `>>>` operator acts as an alias for `andThen`.

3. **compose** — This function functions as the inverse of `andThen`. When provided with two handlers, `h1: B => C` and `h2: A => B`, it returns the function `A => C` via `h1 compose h2`. The `<<<` operator is an alias for `compose`.

4. **zip** — Utilized to merge two handlers into a singular handler, producing a tuple comprising the outputs of both handlers. `zipLeft` and `zipRight` are utilized when only one handler's output is of interest. The `zip` operator is alias for `zip`, while `<*` and `*>` serve as aliases for `zipLeft` and `zipRight` respectively.

5. **orElse** — This function combines two handlers into one, attempting the first handler and proceeding to the second if the first one fails. The `<>` operator is an alias for `orElse`.


| Operator | Explanation                                                        | Variations                                     |
|----------|--------------------------------------------------------------------|------------------------------------------------|
| flatMap  | Used to compose two handlers using monadic composition.            | `flatMap`, `>>=`                               |
| andThen  | Feed the output of the first handler to input of the next handler. | `andThen`, `>>>`                               |
| compose  | Feed the output of the next handler to input of the first handler. | `compose`, `<<<`                               |
| zip      | Combines two handlers into one returning a tuple of outputs.       | `zip`, `zipLeft`, `zipRight`, `<*>` `<*`, `*>` |
| orElse   | Combines two handlers, trying the first and then the second.       | `orElse`, `<>`                                 |

### Error Management

Like `ZIO` data type, the `Handler` has various operators for handling errors, such as `orDie*`, `refineOrDie`, `catchAll*`, `unrefine*`.

These are similar to the `ZIO` ones, but they are specialized for the `Handler` type. If you're unfamiliar with these operators, it's recommended to explore the [Error Management](https://zio.dev/reference/error-management/) section in the core ZIO documentation.

### Working with Environment and Layers

The first type parameter of the `Handler` is the environment type. This means that a `Handler` can require an environment to run, like a `ZIO` effect. When we create a `Handler`, we can get access to the environment using `ZIO.service*` methods, and finally, we can provide the environment using `Handler#provide*` methods.

:::note
Please note that in most cases, we are not required to provide the environment of the handler in the middle of the routes definition. It is usually done at the end when we are creating the `Routes` using the `Server#serve` method.
:::

:::note
If you are not familiar with the concept of environment and layers in ZIO, it is recommended to explore the [Introduction to the ZIO's Contextual Data Types](https://zio.dev/reference/contextual/) section in the core ZIO documentation.
:::

### Adding Delays

When we have a `Handler` of type `In => Out`, we can delay the consumption of the input by using the `Handler#delayBefore` and delay the production of the output by using the `Handler#delayAfter`.

### Converting to Constant Values/Types

To convert a `Handler` to a constant value, we can use the `Handler#as` method. It takes a value of type `Out` and returns a `Handler` that always returns the given value.

We can also narrow or widen the type of environment, error, input, or output of a `Handler` using the `Handler#asEnvType`, `Handler#asErrorType`, `Handler#asInType`, and `Handler#asOutType` methods.

### Overwriting the `Method`

The `Handler.method` overwrites the method in the incoming request to the `Handler`:

```scala mdoc:silent
val handler11 = Handler.fromFunction((request: Request) => Response.text(request.method.toString))
handler11.method(Method.POST)
```

### Patching the `Response`

The `Handler.patch` patches the response produced by the request handler using a `Patch`:

```scala mdoc:silent
val handler12 = Handler.fromResponse(Response.text("Hello World!"))
val handler13 = handler12.patch(Response.Patch.status(Status.Accepted))
```
