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
import zhttp.http._

val app = HttpApp.text("Hello World!")
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

## ZIO Integration

```scala
val app = Http.collectM[Request] {
  case Method.GET -> Root / "hello" => ZIO.succeed(Response.text("Hello World"))
}
```

`Http.collectM` allow routes to return a ZIO effect value.

## Accessing the Request

```scala
import zhttp.http._

val app = Http.collect[Request] {
  case req @ Method.GET -> Root / "fruits" / "a"  =>
    Response.text("URL:" + req.url.path.asString + " Headers: " + r.headers)
  case req @ Method.POST -> Root / "fruits" / "a" =>
    Response.text(req.getBodyAsString.getOrElse("No body!"))
}
```

## Testing

zhttp provides a `zhttp-test` package for use in unit tests. You can utilize it as follows:

```scala
import zio.test._
import zhttp.test._
import zhttp.http._

object Spec extends DefaultRunnableSpec {
  val app = Http.collect[Request] {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
  }
  
  def spec = suite("http") (
    testM("should be ok") {
      val req         = ???
      val expectedRes = resp => resp.status.toJHttpStatus.code() == Status.OK
      assertM(app(req))(expectedRes) // an apply method is added via `zhttp.test` package
    }
  )
}
```

```scala
import zhttp.http._

val app = Http.collect[Request] {
  case req @ Method.GET -> Root / "fruits" / "a"  =>
    Response.text("URL:" + req.url.path.asString + " Headers: " + r.headers)
  case req @ Method.POST -> Root / "fruits" / "a" =>
    Response.text(req.getBodyAsString.getOrElse("No body!"))
}
```

# Socket

## Creating a socket app

```scala
import zhttp.socket._

private val socket = Socket.collect[WebSocketFrame] {
  case WebSocketFrame.Text("FOO")  => ZStream.succeed(WebSocketFrame.text("BAR"))
}

private val app = HttpApp.collect {
  case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings {$name}!")
  case Method.GET -> Root / "ws" => Response.socket(socket)
}
```

# Server

## Starting an Http App

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends App {
  val app = HttpApp.ok

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
```

A simple Http app that responds with empty content and a `200` status code is deployed on port `8090` using `Server.start`.

# Examples

- [Simple Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorld.scala)
- [Advanced Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorldAdvanced.scala)
- [WebSocket Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SocketEchoServer.scala)
- [Streaming Response](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/StreamingResponse.scala)
- [Simple Client](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SimpleClient.scala)
- [File Streaming](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/FileStreaming.scala)
- [Authentication](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/Authentication.scala)
