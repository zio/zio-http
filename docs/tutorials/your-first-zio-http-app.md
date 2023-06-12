---
id: your-first-zio-http-app
title: Your First Zio http app
---

# your first  ZIO HTTP

This tutorial will guide you through the basics of ZIO HTTP, a powerful library for building highly performant HTTP-based services and clients using functional Scala and ZIO. ZIO HTTP uses Netty as its core and provides functional domains for creating, modifying, and composing HTTP apps easily.

## Prerequisites

Before you begin, make sure you have the following:

- Basic knowledge of Scala programming language
- Familiarity with functional programming concepts
- SBT or another build tool for Scala projects

## Installation

To use ZIO HTTP in your Scala project, you need to add the necessary dependencies to your build configuration. Add the following lines to your `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "<version>",
  "dev.zio" %% "zio-http" % "<version>"
)
```

Replace `<version>` with the desired version of ZIO and ZIO HTTP. You can find the latest versions on the [ZIO GitHub page](https://github.com/zio/zio) and [ZIO HTTP GitHub page](https://github.com/zio/zio-http).

After updating the build configuration, refresh your project dependencies.

## Creating an HTTP App

The first step when using ZIO HTTP is to create an HTTP app. The `Http` domain provides various constructors for creating HTTP apps based on different request and response types. Here's an example of creating a "Hello World" app:

```scala
import zio.http._

val app = Http.text("Hello World!")
```

In the above example, we use the `Http.text` constructor to create an HTTP app that always responds with the text "Hello World!".

You can explore other constructors like `Http.html`, `Http.fromFile`, `Http.fromData`, `Http.fromStream`, and `Http.fromEffect` to handle different types of responses.

## Routing

ZIO HTTP provides the `collect` method in the `Http` domain for handling routes. It allows you to define different patterns for requests and produce corresponding responses. Here's an example of creating routes:

```scala
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> !! / "fruits" / "a"  => Response.text("Apple")
  case Method.GET -> !! / "fruits" / "b"  => Response.text("Banana")
}
```

In the above example, we define two routes: one for `/fruits/a` and another for `/fruits/b`. For a GET request to `/fruits/a`, the response will be "Apple", and for a GET request to `/fruits/b`, the response will be "Banana".

You can also create typed routes by extracting values from the URL path. Here's an example that accepts the count as an `Int`:

```scala
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> !! / "Apple" / int(count)  => Response.text(s"Apple: $count")
}
```

In the above example, the route will match URLs like `/Apple/10`, where `10` will be extracted as an `Int` and used in the response.

## Composition

ZIO HTTP allows you to compose multiple apps using operators in the `Http` domain. Two commonly used operators are `++` and `<>`.

Using the `++` operator, you can combine multiple apps, and if none of the routes match in the first app, the control is passed to the next app. Here's an example:

```scala
import zio.http._

val a = Http.collect[Request] { case Method.GET ->

 !! / "a"  => Response.ok }
val b = Http.collect[Request] { case Method.GET -> !! / "b"  => Response.ok }

val app = a ++ b
```

In the above example, the `app` combines two apps `a` and `b`. If a GET request matches `/a`, the response will be provided by app `a`, and if it matches `/b`, the response will be provided by app `b`.

Using the `<>` operator, you can handle failure scenarios. If the first app fails, the control is passed to the next app. Here's an example:

```scala
import zio.http._

val a = Http.fail(new Error("SERVER_ERROR"))
val b = Http.text("OK")

val app = a <> b
```

In the above example, the `app` first tries app `a`. If app `a` fails, it falls back to app `b`, which responds with "OK".

## ZIO Integration

ZIO HTTP seamlessly integrates with ZIO, allowing you to create effectful apps. You can use the `collectZIO` method and wrap the `Response` with `UIO` to produce a ZIO effect value. Here's an example:

```scala
import zio.http._

val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "hello" => UIO(Response.text("Hello World"))
}
```

In the above example, the `app` handles a GET request to `/hello` and responds with "Hello World" wrapped in a ZIO effect.

## Accessing the Request

To access the request details, you can use the `@` symbol to bind the matched pattern to a variable. This allows you to use the request details while creating a response. Here's an example:

```scala
import zio.http._

val app = Http.collectZIO[Request] {
  case req @ Method.GET -> !! / "fruits" / "a"  =>
    UIO(Response.text("URL:" + req.url.path.asString + " Headers: " + req.getHeaders))
  case req @ Method.POST -> !! / "fruits" / "a" =>
    req.bodyAsString.map(Response.text(_))
}
```

In the above example, the `app` handles GET and POST requests to `/fruits/a`. For the GET request, it constructs a response with the URL and headers, and for the POST request, it uses the request body in the response.

## Testing

You can test your HTTP apps using the ZIO Test framework. Since the `Http` domain is a function, you can call it like any other function and assert the expected responses. Here's an example:

```scala
import zio.test._
import zio.http._

object Spec extends DefaultRunnableSpec {

  def spec = suite("http")(
      test("should be ok") {
        val app = Http.ok
        val req = Request()
        assertZIO(app(req))(equalTo(Response.ok))
      }
    )
}
```

In the above example, the test checks if the `app` responds with an `Ok` response when given a request.

## Socket

ZIO HTTP also provides a functional domain called `Socket` for handling WebSocket connections. You can create socket apps using `Socket` constructors. Here's an example of a simple socket app:

```scala
import zio.socket._

private val socket = Socket.collect[WebSocketFrame] {
  case WebSocketFrame.Text("FOO") =>
    ZStream.succeed(WebSocketFrame.text("BAR"))
}

private val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "greet" / name => UIO(Response

.text(s"Greetings {$name}!"))
  case Method.GET -> !! / "ws"           => socket.toResponse
}
```

In the above example, the `socket` collects WebSocket frames and responds with "BAR" when it receives "FOO" as a `WebSocketTextFrame`. The `app` also handles a GET request to `/greet/:name` and responds with a greeting message.

## Server

To serve HTTP requests, you need to start an HTTP server. ZIO HTTP provides a way to configure your server according to your needs. You can specify the server configuration, such as the leak detection level, request size limits, and address. Here's an example of starting an HTTP server:

```scala
import zio.http._
import zio.http.Server
import zio._

object HelloWorld extends App {
  val app = Http.ok

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
```

In the above example, the `app` is a simple HTTP app that responds with an empty content and a 200 status code. The server is started on port 8090 using `Server.start`.

## Conclusion

In this tutorial, you learned the basics of getting started with ZIO HTTP. You learned how to create HTTP apps, handle routing, compose apps, integrate with ZIO, access request details, test your apps, work with sockets, and start an HTTP server. With ZIO HTTP, you can build highly performant and functional HTTP-based services and clients in Scala.

Feel free to explore the ZIO HTTP documentation and experiment with different features to build your own applications. Happy coding!