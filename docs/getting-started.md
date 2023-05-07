---
id: getting-started
title: Getting Started
---

**ZIO HTTP** is a powerful library that is used to build highly performant HTTP-based services and clients using
functional scala and ZIO and uses [Netty](https://netty.io/) as its core.

ZIO HTTP has powerful functional domains which help in creating, modifying, composing apps easily. Let's start with the
HTTP domain.

The first step when using ZIO HTTP is creating an HTTP app.

## Http and Handler

`Handler` describes the transformation from an incoming `Request` to an outgoing `Response` type. The `Http` type on top
if this
provides input-dependent routing to different `Handler` values. There are some default handler constructors such
as `Handler.text`, `Handler.html`, `Handler.fromFile`, `Handler.fromData`, `Handler.fromStream`, `Handler.fromEffect`.

A `Handler` can always be transformed to a `Http` value using the `.toHttp` method.

### Creating a "_Hello World_" app

Creating an HTTP app using ZIO Http is as simple as given below, this app will always respond with "Hello World!"

```scala mdoc:silent
import zio.http._

val app = Handler.text("Hello World!").toHttp
```

An application can be made using any of the available operators on `zio.Http`. In the above program for any Http
request, the response is always `"Hello World!"`.

### Routing

For handling routes, Http Domain has a `collect` method that, accepts different requests and produces responses. Pattern
matching on the route is supported by the framework.
The example below shows how to create routes:

```scala mdoc:silent:reset
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> !! / "fruits" / "a" => Response.text("Apple")
  case Method.GET -> !! / "fruits" / "b" => Response.text("Banana")
}
```

You can create typed routes as well. The below example shows how to accept count as `Int` only:

 ```scala mdoc:silent:reset
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> !! / "Apple" / int(count) => Response.text(s"Apple: $count")
}
 ```

Pattern matching on route is supported by the framework

### Composition

Apps can be composed using operators in `Http`:

- Using the `++` operator. The way it works is, if none of the routes match in `a`, then the control is passed on to
  the `b` app:

```scala mdoc:silent:reset
import zio.http._

val a = Http.collect[Request] { case Method.GET -> !! / "a" => Response.ok }
val b = Http.collect[Request] { case Method.GET -> !! / "b" => Response.ok }

val app = a ++ b
```

- Using the `<>` operator. The way it works is, if `a` fails, then the control is passed on to the `b` app:

```scala mdoc:silent:reset
import zio.http._

val a = Handler.fail(new Error("SERVER_ERROR"))
val b = Handler.text("OK")

val app = (a <> b).toHttp
```

### ZIO Integration

For creating effectful apps, you can use `collectZIO` and wrap `Response` with `ZIO` to produce ZIO effect value.

```scala mdoc:silent:reset
import zio.http._
import zio._

val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "hello" => ZIO.succeed(Response.text("Hello World"))
}
```

### Accessing the Request

To access the request use `@` as it binds a matched pattern to a variable and can be used while creating a response:

```scala mdoc:silent:reset
import zio.http._
import zio._

val app = Http.collectZIO[Request] {
  case req@Method.GET -> !! / "fruits" / "a" =>
    ZIO.succeed(Response.text("URL:" + req.url.path.toString + " Headers: " + req.headers))
  case req@Method.POST -> !! / "fruits" / "a" =>
    req.body.asString.map(Response.text(_))
}
```

### Testing

You can run `Http` as a function of `A => ZIO[R, Option[E], B]` to test it by using the `runZIO` method.

```scala mdoc:silent:reset
import zio.test._
import zio.test.Assertion.equalTo
import zio.http._

object Spec extends ZIOSpecDefault {

  def spec = suite("http")(
    test("should be ok") {
      val app = Handler.ok.toHttp
      val req = Request.get(URL(!!))
      assertZIO(app.runZIO(req))(equalTo(Response.ok))
    }
  )
}
```

When we call the `app` with the `request` it calls the apply method of `Http` via `zio.test` package

## Socket

`Socket` is functional domain in ZIO HTTP. It provides constructors to create socket apps.
A socket app is an app that handles WebSocket connections.

### Creating a socket app

Socket app can be created by using `Socket` constructors. To create a socket app, you need to create a socket that
accepts `WebSocketChannel` and produces `ZIO`.
Finally, we need to convert socketApp to `Response` using `toResponse`, so that we can run it like any other HTTP
app.   
The below example shows a simple socket app, we are using `collectZIO` which sends WebsSocketTextFrame "
BAR" on receiving WebsSocketTextFrame "FOO".

```scala mdoc:silent:reset
import zio.http._
import zio.stream._
import zio._

private val socket =
  Http.webSocket { channel =>
    channel.receiveAll {
      case ChannelEvent.Read(WebSocketFrame.Text("FOO")) =>
        channel.send(ChannelEvent.Read(WebSocketFrame.text("BAR")))
      case _ =>
        ZIO.unit
    }
  }

private val app = 
  Http.collectZIO[Request] {
    case Method.GET -> !! / "greet" / name => ZIO.succeed(Response.text(s"Greetings {$name}!"))
    case Method.GET -> !! / "ws" => socket.toSocketApp.toResponse
  }
```

## Server

As we have seen how to create HTTP apps, the only thing left is to run an HTTP server and serve requests.
ZIO HTTP provides a way to set configurations for your server. The server can be configured according to the leak
detection level, request size, address etc.

### Starting an HTTP App

To launch our app, we need to start the server on a port. The below example shows a simple HTTP app that responds with
empty content and a `200` status code, deployed on port `8090` using `Server.start`.

```scala mdoc:silent:reset
import zio.http._
import zio._

object HelloWorld extends ZIOAppDefault {
  val app = Handler.ok.toHttp

  override def run =
    Server.serve(app).provide(Server.defaultWithPort(8090))
}
```

## Examples

- [HTTP Server](https://zio.github.io/zio-http/docs/v1.x/examples/zio-http-basic-examples/http_server)
- [Advanced Server](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/advanced_server)
- [WebSocket Server](https://zio.github.io/zio-http/docs/v1.x/examples/zio-http-basic-examples/web-socket)
- [Streaming Response](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/stream-response)
- [HTTP Client](https://zio.github.io/zio-http/docs/v1.x/examples/zio-http-basic-examples/http_client)
- [File Streaming](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/stream-file)
- [Authentication](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/authentication)
