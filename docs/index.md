**Table of Contents**

- [Http](#http)
  - [Creating a "_Hello World_" app](#creating-a-hello-world-app)
  - [Routing](#routing)
  - [Composition](#composition)
  - [ZIO Integration](#zio-integration)
  - [Requests](#accessing-the-request)
  - [Testing](#testing)
- [Socket](#socket)
  - [Creating a socket app](#creating-a-socket-app)
- [Server](#server)
  - [Starting an Http App](#starting-an-http-app)
- [Examples](#examples)

# Http

## Creating a "_Hello World_" app

```scala
import zio.http._

val app = Http.text("Hello World!")
```

An application can be made using any of the available operators on `zio.Http`. In the above program for any Http request, the response is always `"Hello World!"`.

## Routing

```scala
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> Root / "fruits" / "a"  => Response.text("Apple")
  case Method.GET -> Root / "fruits" / "b"  => Response.text("Banana")
}
```

Pattern matching on route is supported by the framework

## Composition

```scala
import zio.http._

val a = Http.collect[Request] { case Method.GET -> Root / "a"  => Response.ok }
val b = Http.collect[Request] { case Method.GET -> Root / "b"  => Response.ok }

val app = a <> b
```

Apps can be composed using the `<>` operator. The way it works is, if none of the routes match in `a` , or a `NotFound` error is thrown from `a`, and then the control is passed on to the `b` app.

## ZIO Integration

```scala
val app = Http.collectM[Request] {
  case Method.GET -> Root / "hello" => ZIO.succeed(Response.text("Hello World"))
}
```

`Http.collectM` allow routes to return a ZIO effect value.

## Accessing the Request

```scala
import zio.http._

val app = Http.collect[Request] {
  case req @ Method.GET -> Root / "fruits" / "a"  =>
    Response.text("URL:" + req.url.path.asString + " Headers: " + r.headers)
  case req @ Method.POST -> Root / "fruits" / "a" =>
    Response.text(req.bodyAsString.getOrElse("No body!"))
}
```

## Testing

Tests suites could be implemented using `zio-test` library, as following:

```scala

import zio.test._
import zio.http._
import zio.test.Assertion.equalTo

object Spec extends DefaultRunnableSpec {
  val app = Http.collect[Request] { case Method.GET -> !! / "text" =>
    Response.text("Hello World!")
  }

  def spec = suite("http")(
    test("should be ok") {
      val req         = ???
      val expectedRes = app(req).map(_.status)
      assertZIO(expectedRes)(equalTo(Status.Ok))
    },
  )
}
```

# Socket

## Creating a socket app

```scala
import zio.socket._

private val socket = Socket.collect[WebSocketFrame] {
  case WebSocketFrame.Text("FOO")  => ZStream.succeed(WebSocketFrame.text("BAR"))
}

private val app = Http.collect[Request] {
  case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings {$name}!")
  case Method.GET -> Root / "ws" => Response.socket(socket)
}
```

# Server

## Starting an Http App

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

A simple Http app that responds with empty content and a `200` status code is deployed on port `8090` using `Server.start`.

# Examples

- [Simple Server](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorld.scala)
- [Advanced Server](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorldAdvanced.scala)
- [WebSocket Server](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/SocketEchoServer.scala)
- [Streaming Response](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/StreamingResponse.scala)
- [Simple Client](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/SimpleClient.scala)
- [File Streaming](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/FileStreaming.scala)
- [Authentication](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/Authentication.scala)
