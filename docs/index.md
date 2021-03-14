**Table of Contents**

- [Http](#http)
  - [Creating a "_Hello World_" app](#creating-a-hello-world-app)
  - [Routing](#routing)
  - [Composition](#composition)
- [Socket](#socket)
  - [Creating a socket app](#creating-a-socket-app)
  - [WebSocket Support](#websocket-support)
- [Server](#server)
  - [Starting an Http App](#starting-an-http-app)
  - [Advanced Usage](#advanced-usage)
  - [Performance Tuning](#performance-tuning)
- [Client](#client)

# Http

## Creating a "_Hello World_" app

```scala
import zhttp.http._

val app = Http.text("Hello World!")
```

An application can be made using any of the available operators on `zhttp.Http`. In the above program for any Http request, the response is always `"Hello World!"`.

## Routing

```scala
import zhttp.http._

val app = Http.collect[Request] {
  case Method.GET -> Root / "fruits" / "a"  => Response.text("Apple")
  case Method.GET -> Root / "fruits" / "b"  => Response.text("Banana")
}
```

Pattern matching on route is supported by the framework

## Composition

```scala
import zhttp.http._

val a = Http.collect[Request] { case Method.GET -> Root / "a"  => Response.ok }
val b = Http.collect[Request] { case Method.GET -> Root / "b"  => Response.ok }

val app = a <> b
```

Apps can be composed using the `<>` operator. The way it works is, if none of the routes match in `a` , or a `NotFound` error is thrown from `a`, and then the control is passed on to the `b` app.

# Socket

## Creating a socket app

```scala
import zhttp.socket._

val socket = Socket.forall(_ => ZStream.repeat(WebSocketFrame.text("Hello!")).take(10))
```

## WebSocket Support

ZIO Http comes with first-class support for web sockets.

```scala
import zhttp.socket._
import zhttp.http._
import zio.stream._

val socket = Socket.forall(_ => ZStream.repeat(WebSocketFrame.text("Hello!")).take(10))

val app = Http.collect[Request] {
  case Method.GET -> Root / "health"       => Response.ok
  case _          -> Root / "subscription" => socket.asResponse
}

```

# Server

## Starting an Http App

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends App {
  val app = Http.ok

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
```

A simple Http app that responds with empty content and a `200` status code is deployed on port `8090` using `Server.start`.

## Advanced Usage

???

## Performance Tuning

???

# Client

???
