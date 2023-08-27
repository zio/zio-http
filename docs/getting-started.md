---
id: getting-started
title: Getting Started
---

**ZIO HTTP** is a powerful library that is used to build highly performant HTTP-based services and clients using functional scala and ZIO and uses [Netty](https://netty.io/) as its core.

ZIO HTTP has powerful functional domains that help in creating, modifying, composing apps easily. Let's start with the HTTP domain.

The first step when using ZIO HTTP is creating an HTTP app.

## Http and Handler

`Handler` describes the transformation from an incoming `Request` to an outgoing `Response` type. The `HttpApp` 
type on top of this  provides input-dependent routing to different `Handler` values. There are some default 
handler constructors such as `Handler.text`, `Handler.html`, `Handler.fromFile`, `Handler.fromData`, `Handler.fromStream`, `Handler.fromEffect`.

A `Handler` can always be transformed to a `HttpApp` value using the `.toHttpApp` method, in which case the 
HTTP application will handle all routes.

### Creating a "_Hello World_" app

Creating an HTTP app using ZIO HTTP is as simple as given below, this app will always respond with "Hello World!"

```scala mdoc:silent
import zio.http._

val app = Handler.text("Hello World!").toHttpApp
```

An application can be made using any of the available operators on `HttpApp`. In the above program for any Http request, the response is always `"Hello World!"`.

### Routing

For handling routes, ZIO HTTP has a `Routes` value, which allows you to aggregate a collection of 
individual routes.

Behind the scenes, ZIO HTTP builds an efficient prefix-tree whenever needed to optimize dispatch.

The example below shows how to create routes:

```scala mdoc:silent:reset
import zio.http._

val routes = Routes(
  Method.GET / "fruits" / "a" -> handler(Response.text("Apple")),
  Method.GET / "fruits" / "b" -> handler(Response.text("Banana"))
)
```

You can create parameterized routes as well:

```scala mdoc:silent:reset
import zio.http._

val routes = Routes(
  Method.GET / "Apple" / int("count") ->
    handler { (count: Int, req: Request) =>
      Response.text(s"Apple: $count")
    }
)
```

### Composition

Routes can be composed using the `++` operator, which works by combining the routes.

```scala mdoc:silent:reset
import zio.http._

val a = Routes(Method.GET / "a" -> Handler.ok)
val b = Routes(Method.GET / "b" -> Handler.ok)

val routes = a ++ b
```

### ZIO Integration

For creating effectful apps, you can use handlers that return ZIO effects:

```scala mdoc:silent:reset
import zio.http._
import zio._

val routes = Routes(
  Method.GET / "hello" -> handler(ZIO.succeed(Response.text("Hello World")))
)
```

### Accessing the Request

To access the request, just create a handler that accepts the request:

```scala mdoc:silent:reset
import zio.http._
import zio._

val routes = Routes(
  Method.GET / "fruits" / "a" -> handler { (req: Request) =>
    Response.text("URL:" + req.url.path.toString + " Headers: " + req.headers)
  },

  Method.POST / "fruits" / "a" -> handler { (req: Request) =>
    req.body.asString.map(Response.text(_))
  }
)
```

### Testing

You can run `HttpApp` as a function of `A => ZIO[R, Response, Response]` to test it by using the `runZIO` method.

```scala mdoc:silent:reset
import zio.test._
import zio.test.Assertion.equalTo
import zio.http._

object Spec extends ZIOSpecDefault {

  def spec = suite("http")(
    test("should be ok") {
      val app = Handler.ok.toHttpApp
      val req = Request.get(URL(Root))
      assertZIO(app.runZIO(req))(equalTo(Response.ok))
    }
  )
}
```

## Socket

`Socket` is functional domain in ZIO HTTP. It provides constructors to create socket apps. A socket app is 
an app that handles WebSocket connections.

### Creating a socket app

Socket app can be created by using `Socket` constructors. To create a socket app, you need to create a socket that accepts `WebSocketChannel` and produces `ZIO`. Finally, we need to convert socketApp to `Response` using `toResponse`, so that we can run it like any other HTTP app.   

The below example shows a simple socket app,  which sends WebsSocketTextFrame "
BAR" on receiving WebsSocketTextFrame "FOO".

```scala mdoc:silent:reset
import zio.http._
import zio.stream._
import zio._

val socket =
  Handler.webSocket { channel =>
    channel.receiveAll {
      case ChannelEvent.Read(WebSocketFrame.Text("FOO")) =>
        channel.send(ChannelEvent.Read(WebSocketFrame.text("BAR")))
      case _ =>
        ZIO.unit
    }
  }

val routes = 
  Routes(
    Method.GET / "greet" / string("name") -> handler { (name: String, req: Request) => 
      Response.text(s"Greetings {$name}!")
    },
    Method.GET / "ws" -> handler(socket.toResponse)
  )
```

## Server

As we have seen how to create HTTP apps, the only thing left is to run an HTTP server and serve requests.
ZIO HTTP provides a way to set configurations for your server. The server can be configured according to the leak detection level, request size, address etc.

### Starting an HTTP App

To launch our app, we need to start the server on a port. The below example shows a simple HTTP app that responds with empty content and a `200` status code, deployed on port `8090` using `Server.start`.

```scala mdoc:silent:reset
import zio.http._
import zio._

object HelloWorld extends ZIOAppDefault {
  val app = Handler.ok.toHttpApp

  override def run =
    Server.serve(app).provide(Server.defaultWithPort(8090))
}
```

## Examples

- [HTTP Server](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorld.scala)
- [WebSocket Server](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/WebSocketEcho.scala)
- [Streaming Response](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/RequestStreaming.scala)
- [HTTP Client](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HttpsClient.scala)
- [File Streaming](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/FileStreaming.scala)
- [Authentication](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/AuthenticationServer.scala)
- [All examples](https://github.com/zio/zio-http/tree/main/zio-http-example/src/main/scala/example)