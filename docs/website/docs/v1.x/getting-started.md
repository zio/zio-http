---
sidebar_position: 2
---

# Getting Started

## Http

### Creating a "_Hello World_" app

```scala
import zhttp.http._

val app = Http.text("Hello World!")
```

An application can be made using any of the available operators on `zhttp.Http`. In the above program for any Http request, the response is always `"Hello World!"`.

### Routing

```scala
import zhttp.http._

val app = Http.collect[Request] {
  case Method.GET -> !! / "fruits" / "a"  => Response.text("Apple")
  case Method.GET -> !! / "fruits" / "b"  => Response.text("Banana")
}
```

Pattern matching on route is supported by the framework

### Composition

```scala
import zhttp.http._

val a = Http.collect[Request] { case Method.GET -> !! / "a"  => Response.ok }
val b = Http.collect[Request] { case Method.GET -> !! / "b"  => Response.ok }

val app = a ++ b
```

Apps can be composed using the `++` operator. The way it works is, if none of the routes match in `a` , then the control is passed on to the `b` app.

### ZIO Integration

```scala
val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "hello" => Response.text("Hello World").wrapZIO
}
```

`Http.collectZIO` allow routes to return a ZIO effect value.

### Accessing the Request

To access request use @ as it binds a matched pattern to a variable and can be used in creating response.  
```scala
import zhttp.http._

val app = Http.collectZIO[Request] {
    case req @ Method.GET -> !! / "fruits" / "a"  =>
      Response.text("URL:" + req.url.path.asString + " Headers: " + req.getHeaders).wrapZIO
    case req @ Method.POST -> !! / "fruits" / "a" =>
      req.getBodyAsString.map(Response.text(_))
  }
```

### Testing

Since `Http` is a function of the form `A => ZIO[R, Option[E], B]` to test it you can simply call an `Http` like a function.

```scala
import zio.test._
import zhttp.http._

object Spec extends DefaultRunnableSpec {

  def spec = suite("http")(
      testM("should be ok") {
        val app = Http.ok
        val req = Request()
        assertM(app(req))(equalTo(Response.ok))
      }
    )
}
```
When we call `app` with `request` it calls apply method of Http via `zhttp.test` package

## Socket

### Creating a socket app

Let's build an app which can handle websocket connections as well. To make one, we need to create a socket app and then convert it to Response using `toResponse`.
Socket app can be created by using `Socket` constructors. Here we are using `collect` which returns a stream with WebsSocketTextFrame "BAR" on receiving WebsSocketTextFrame "FOO".   
```scala
import zhttp.socket._

private val socket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Text("FOO") =>
    ZStream.succeed(WebSocketFrame.text("BAR"))
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "greet" / name => Response.text(s"Greetings {$name}!").wrapZIO
    case Method.GET -> !! / "ws"           => socket.toResponse
  }
```

## Server

### Starting an Http App

To Launch our app, we need to start server on some port. Let's see an example of a simple Http app that responds with empty content and a `200` status code is deployed on port `8090` using `Server.start`.
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
Server can be configured according to the leak detection level, request size, address etc. 


## Examples

- [Simple Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorld.scala)
- [Advanced Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorldAdvanced.scala)
- [WebSocket Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SocketEchoServer.scala)
- [Streaming Response](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/StreamingResponse.scala)
- [Simple Client](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SimpleClient.scala)
- [File Streaming](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/FileStreaming.scala)
- [Authentication](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/Authentication.scala)
