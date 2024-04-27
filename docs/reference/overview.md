---
id: overview
title: Introduction to ZIO HTTP
---

ZIO HTTP offers an expressive API for creating HTTP applications. It uses a domain-specific language (DSL) to define routes and handlers. Both server and client are designed in terms of **HTTP as a function**, so they are functions from `Request` to `Response`.

## Core Concepts

ZIO HTTP has powerful functional domains that help in creating, modifying, and composing apps easily. Let's take a look at the core domain:

- `Routes` - A collection of `Route`s. If the error type of the routes is `Response`, then they can be served.
- `Route` - A single route that can be matched against an HTTP `Request` and produce a `Response`. It comprises a `RoutePattern` and a `Handler`:
  1. `RoutePattern` - A pattern that can be matched against an HTTP `Request`. It is a combination of `Method` and `PathCodec` which can be used to match the `Method` and `Path` of the `Request`.
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
      req.body.asString.map(Response.text(_))
    }

  // The Routes that don't require any service from the ZIO environment,
  // so the first type parameter is Any.
  // All the errors are handled by turning them into a Response.
  val routes: Routes[Any, Response] =
    // List of all the routes
    Routes(greetRoute, echoRoute)
            // Handle all unhandled errors
            .handleError(e => Response.internalServerError(e.getMessage))

  // Serving the routes using the default server layer on port 8080
  def run = Server.serve(routes).provide(Server.default)
}
```

### 1.Routes 

The `Routes` is a collection of `Route` values. It can be created using its default constructor:

```scala mdoc:invisible
import zio.http._

val greetRoute: Route[Any, Nothing] = Route.notFound
val echoRoute: Route[Any, Throwable] = Route.notFound
```

```scala mdoc:silent
import zio.http._

val app: Routes[Any, Response] =
  Routes(greetRoute, echoRoute)
    .handleError(e => Response.internalServerError(e.getMessage))
```

The `Handler` and `Route` can be transformed to `Routes` by the `.toRoutes` method. To serve the routes, all errors should be handled by converting them into a `Response` using for example the `.handleError` method.

For handling routes, ZIO HTTP has a [`Routes`](routing/routes.md) value, which allows us to aggregate a collection of individual routes. Behind the scenes, ZIO HTTP builds an efficient prefix-tree whenever needed to optimize dispatch.

### 2. Route

Each `Route` is a combination of a [`RoutePattern`](routing/route_pattern.md) and a [`Handler`](handler.md). The `RoutePattern` is a combination of a `Method` and a [`PathCodec`](routing/path_codec.md) that can be used to match the method and path of the request. The `Handler` is a function that can convert a `Request` into a `Response`.

The `PathCodec` can be parameterized to extract values from the path. In such cases, the `Handler` should be a function that accepts the extracted values besides the `Request`:

```scala mdoc:silent:nest
import zio.http._

val routes = Routes(
  Method.GET / "user" / int("id") ->
    handler { (id: Int, req: Request) =>
      Response.text(s"Requested User ID: $id")
    }
)
```

To learn more about routes, see the [Routes](routing/routes.md) page.

### 3. Handler

The `Handler` describes the transformation from an incoming `Request` to an outgoing `Response`:

```scala mdoc:compile-only
val helloHandler =
  handler { (_: Request) =>
    Response.text("Hello World!")
  }
```

The `Handler` can be effectful, in which case it should be a function that returns a `ZIO` effect, e.g.:

```scala mdoc:compile-only
val randomGeneratorHandler = 
  handler { (_: Request) =>
    Random.nextIntBounded(100).map(_.toString).map(Response.text(_))
  }
```

There are several ways to create a `Handler`, to learn more about handlers, see the [Handlers](reference/handler.md) page.

## Accessing the Request

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

To learn more about the request, see the [Request](request.md) page.

## Accessing Services from The Environment

ZIO HTTP is built on top of ZIO, which means that we can access services from the environment in our handlers. For example, we can access a `Ref[Int]` service to create a simple counter:

```scala mdoc:compile-only
import zio._
import zio.http._

object CounterExample extends ZIOAppDefault {
  val routes: Routes[Ref[Int], Response] =
    Routes(
      Method.GET / "count" / int("n") ->
              handler { (n: Int, _: Request) =>
                for {
                  ref <- ZIO.service[Ref[Int]]
                  res <- ref.updateAndGet(_ + n)
                } yield Response.text(s"Counter: $res")
              },
      )

  def run = Server.serve(routes).provide(Server.default, ZLayer.fromZIO(Ref.make(0)))
}
```

Finally, we should provide the required services to the server using the `provide` method. In the above example, we provided the `Ref[Int]` service using the `ZLayer.fromZIO` method.

## WebSocket Connection

To handle WebSocket connections, we can use `Handler.webSocket` to create a socket app. To create a socket app, we need to create a socket that accepts `WebSocketChannel` and produces `ZIO`. Finally, we need to convert socketApp to `Response` using `toResponse`, so that we can run it like any other HTTP app.

The below example shows a simple socket app, which sends `WebsSocketTextFrame` "BAR" on receiving `WebsSocketTextFrame` "FOO":

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

We have a more detailed explanation of the WebSocket connection on the [Socket](socket/socket.md) page.

## Server

As we have seen how to create HTTP apps, the only thing left is to run an HTTP server and serve requests.

ZIO HTTP provides a way to set configurations for our server. The server can be configured according to the leak detection level, request size, address etc.

To launch our app, we need to start the server on a port. The below example shows a simple HTTP app that responds with empty content and a `200` status code, deployed on port `8090` using `Server.start`:

```scala mdoc:silent:reset
import zio.http._
import zio._

object HelloWorld extends ZIOAppDefault {
  val routes = Handler.ok.toRoutes

  override def run =
    Server.serve(routes).provide(Server.defaultWithPort(8090))
}
```

Finally, we provided the default server with the port `8090` to the app. To learn more about the server, see the [Server](server.md) page.

## Client

Besides creating HTTP apps, ZIO HTTP also provides a way to create HTTP clients. The client can be used to send requests to the server and receive responses:

```scala mdoc:compile-only
import zio._
import zio.http._

object ClientExample extends ZIOAppDefault {

  val app =
    for {
      client   <- ZIO.serviceWith[Client](_.host("localhost").port(8090))
      response <- client.request(Request.get("/"))
      _        <- ZIO.debug("Response Status: " + response.status)
    } yield ()

  def run = app.provide(Client.default, Scope.default)
}
```

In the above example, we obtained the `Client` service from the environment and sent a `GET` request to the server. Finally, to run the client app, we provided the default `Client` and `Scope` services to the app. For more information about the client, see the [Client](client.md) page.