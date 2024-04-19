---
id: overview
title: Overview
---

**ZIO HTTP** is a powerful library that is used to build highly performant HTTP-based services and clients using functional scala and ZIO and uses [Netty](https://netty.io/) as its core.

ZIO HTTP has powerful functional domains that help in creating, modifying, composing apps easily. Let's start with the HTTP domain.

The core concepts of ZIO HTTP are:

- `HttpApp` - A collection of `Routes`s that are ready to be served. All errors are handled through conversion into HTTP responses.
- `Routes` - A collection of `Route`s.
- `Route` - A single route that can be matched against an http `Request` and produce a `Response`. It comprises of a `RoutePattern` and a `Handler`:
  1. `RoutePattern` - A pattern that can be matched against an http request. It is a combination of `Method` and `PathCodec` which can be used to match the method and path of the request.
  2. `Handler` - A function that can convert a `Request` into a `Response`.

Let's see each of these concepts inside a simple example:

```scala mdoc:silent
import zio._
import zio.http._

object ExampleServer extends ZIOAppDefault {

  // A route that matches GET requests to /greet
  // It doesn't require any service from the ZIO environment 
  // so the first type parameter is Any
  // All its errors are handled so the second type parameter is Nothing
  val greetRoute: Route[Any, Nothing] =
    // The whole Method.GET / "greet" is a RoutePattern
    Method.GET / "greet" ->
      // The handler is a function that takes a Request and returns a Response
      handler { (req: Request) =>
       val name = req.queryParamToOrElse("name", "World")
       Response.text(s"Hello $name!")
      }

  // A route that matches POST requests to /echo
  // It doesn't require any service from the ZIO environment
  // It is an unhandled route so the second type parameter is something other than Nothing
  val echoRoute: Route[Any, Throwable] =
    Method.POST / "echo" -> handler { (req: Request) =>
      req.body.asString.map(e => Response.text(e))
    }

  // The HttpApp that doesn't require any service from the ZIO environment,
  // so the first type parameter is Any.
  // All the errors are handled
  val app: HttpApp[Any] =
    // List of all the routes
    Routes(greetRoute, echoRoute) // Handle all unhandled errors
      .handleError(e => Response.internalServerError(e.getMessage))
      .toHttpApp // Convert the routes to an HttpApp
  
  // Serving the app using the default server layer
  def run = Server.serve(app).provide(Server.default)
}
```

## Handler and HttpApp

`Handler` describes the transformation from an incoming `Request` to an outgoing `Response` type. The `HttpApp` type on top of this provides input-dependent routing to different `Handler` values.

There are some default handler constructors such as `Handler.text`, `Handler.html`, `Handler.fromFile`, `Handler.fromBody`, `Handler.fromStream`, `Handler.fromEffect`.

A `Handler` can always be transformed to a `HttpApp` value using the `.toHttpApp` method, in which case the HTTP application will handle all routes.

### Creating a Hello World App

Creating an HTTP app using ZIO HTTP is as simple as given below, this app will always respond with "Hello World!"

```scala mdoc:silent
import zio.http._

val app = Handler.text("Hello World!").toHttpApp
```

An application can be made using any of the available operators on `HttpApp`. In the above program for any Http request, the response is always `"Hello World!"`.

### Routing

For handling routes, ZIO HTTP has a `Routes` value, which allows us to aggregate a collection of individual routes.

Behind the scenes, ZIO HTTP builds an efficient prefix-tree whenever needed to optimize dispatch.

The example below shows how to create routes:

```scala mdoc:silent:reset
import zio.http._

val routes = Routes(
  Method.GET / "fruits" / "a" -> handler(Response.text("Apple")),
  Method.GET / "fruits" / "b" -> handler(Response.text("Banana"))
)
```

We can create parameterized routes as well:

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

For creating effectful apps, we can use handlers that return ZIO effects:

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

## Socket

`Socket` is functional domain in ZIO HTTP. It provides constructors to create socket apps. A socket app is an app that handles WebSocket connections.

### Creating a socket app

Socket app can be created by using `Socket` constructors. To create a socket app, we need to create a socket that accepts `WebSocketChannel` and produces `ZIO`. Finally, we need to convert socketApp to `Response` using `toResponse`, so that we can run it like any other HTTP app.

The below example shows a simple socket app, which sends WebsSocketTextFrame "BAR" on receiving WebsSocketTextFrame "FOO".

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

ZIO HTTP provides a way to set configurations for our server. The server can be configured according to the leak detection level, request size, address etc.

### Starting an HTTP App

To launch our app, we need to start the server on a port. The below example shows a simple HTTP app that responds with empty content and a `200` status code, deployed on port `8090` using `Server.start`:

```scala mdoc:silent:reset
import zio.http._
import zio._

object HelloWorld extends ZIOAppDefault {
  val app = Handler.ok.toHttpApp

  override def run =
    Server.serve(app).provide(Server.defaultWithPort(8090))
}
```
