---
sidebar_position: 2
---

# Getting Started

ZIO HTTP is a powerful library that is used to build highly performant HTTP-based services and clients using functional scala and ZIO and uses Netty as its core.
The first step when using ZIO HTTP is creating an HTTP app. 
ZIO HTTP has some powerful functional domains which help in creating, modifying, composing apps easily.
Let's start with the HTTP domain.

## Http

HTTP is a domain that models Http apps using ZIO and works over any request and response types. 
Http Domain provides a lot of ways to create HTTP apps, for example `Http.text`, `Http.html`, `Http.fromFile`, `Http.fromData`, `Http.fromStream`, `Http.fromEffect`. 

### Creating a "_Hello World_" app

Creating an HTTP app using ZIO Http is as simple as this:

```scala
import zhttp.http._

val app = Http.text("Hello World!")
```
In the above snippet, for any HTTP request, the response is always `"Hello World!"`.
An application can be made using any of the available operators on `zhttp.Http`.

### Routing

 For handling routes, Http Domain has a `collect` method that, accepts different requests and produces responses. Pattern matching on the route is supported by the framework
The example below shows how to create routes:

```scala,
import zhttp.http._

val app = Http.collect[Request] {
  case Method.GET -> !! / "fruits" / "a"  => Response.text("Apple")
  case Method.GET -> !! / "fruits" / "b"  => Response.text("Banana")
}
```
You can create typed routes as well. The below example shows how to accept count as `Int` only.
 ```scala,
 import zhttp.http._
 
 val app = Http.collect[Request] {
   case Method.GET -> !! / "Apple" / int(count)  => Response.text(s"Apple: $count")
 }
 ```

### Composition

HTTP app can be composed using the `++` operator. The way it works is if none of the routes matches in `a` or there is any error in `a`, the control passes on to the `b` app.

```scala
import zhttp.http._

val a = Http.collect[Request] { case Method.GET -> !! / "a"  => Response.ok }
val b = Http.collect[Request] { case Method.GET -> !! / "b"  => Response.ok }

val app = a ++ b
```

Apps can be composed using the `++` operator. The way it works is, if none of the routes match in `a` , then the control is passed on to the `b` app.

### ZIO Integration

For creating effectful apps, you can use `collectZIO` and wrap `Response` using `wrapZIO` to produce ZIO effect value.

```scala
val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "hello" => Response.text("Hello World").wrapZIO
}
```

### Accessing the Request

To access request in the response, use `@` as it binds a matched pattern to a variable and can be used while creating a response.  
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
When we call `app` with `request` it calls apply the method of HTTP via `zhttp.test` package

## Socket

`Socket` is another functional domain in ZIO HTTP. It provides operators to create socket apps. 
A socket app is an app that handles WebSocket connections.

### Creating a socket app

Socket app can be created by using `Socket` constructors. To create a socket app, you need to create a socket that accepts `WebSocketFrame` and produces `ZStream` of `WebSocketFrame`.
Finally, we need to convert socketApp to `Response` using `toResponse`, so that we can run it like any other HTTP app.   
The below example shows a simple socket app, we are using `collect` which returns a stream with WebsSocketTextFrame "BAR" on receiving WebsSocketTextFrame "FOO".   

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

As we have seen how to create HTTP apps, the only thing left is to run an  HTTP server and serve requests.
ZIO HTTP provides a way to set configurations for your server. The server can be configured according to the leak detection level, request size, address etc. 

### Starting an HTTP App

To launch our app, we need to start the server on some port. The below example shows a simple HTTP app that responds with empty content and a `200` status code, deployed on port `8090` using `Server.start`.

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

## Examples

- [Simple Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorld.scala)
- [Advanced Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorldAdvanced.scala)
- [WebSocket Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SocketEchoServer.scala)
- [Streaming Response](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/StreamingResponse.scala)
- [Simple Client](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SimpleClient.scala)
- [File Streaming](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/FileStreaming.scala)
- [Authentication](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/Authentication.scala)
