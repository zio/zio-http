---
sidebar_position: 2
---

# Getting Started

**ZIO HTTP** is a powerful library that is used to build highly performant HTTP-based services and clients using functional scala and ZIO and uses [Netty](https://netty.io/) as its core.
ZIO HTTP has powerful functional domains which help in creating, modifying, composing apps easily. Let's start with the HTTP domain.
The first step when using ZIO HTTP is creating an HTTP app. 

## Http

`Http` is a domain that models HTTP apps using ZIO and works over any request and response types. `Http` Domain provides different constructors to create HTTP apps, `Http.text`, `Http.html`, `Http.fromFile`, `Http.fromData`, `Http.fromStream`, `Http.fromEffect`.  

### Creating a "_Hello World_" app

Creating an HTTP app using ZIO Http is as simple as given below, this app will always respond with "Hello World!"

```scala
import zio.http._

val app = Http.text("Hello World!")
```
An app can be made using any of the available constructors on `zio.Http`.

### Routing

 For handling routes, Http Domain has a `collect` method that, accepts different requests and produces responses. Pattern matching on the route is supported by the framework.
The example below shows how to create routes:

```scala
import zio.http._

val app = Http.collect[Request] {
  case Method.GET -> !! / "fruits" / "a"  => Response.text("Apple")
  case Method.GET -> !! / "fruits" / "b"  => Response.text("Banana")
}
```
You can create typed routes as well. The below example shows how to accept count as `Int` only.
 ```scala
 import zio.http._
 
 val app = Http.collect[Request] {
   case Method.GET -> !! / "Apple" / int(count)  => Response.text(s"Apple: $count")
 }
 ```

### Composition

Apps can be composed using operators in `Http`:

- Using the `++` operator. The way it works is, if none of the routes match in `a`, then the control is passed on to the `b` app.

```scala
 import zio.http._
 
 val a = Http.collect[Request] { case Method.GET -> !! / "a"  => Response.ok }
 val b = Http.collect[Request] { case Method.GET -> !! / "b"  => Response.ok }
 
 val app = a ++ b
 ```


- Using the `<>` operator. The way it works is, if `a` fails, then the control is passed on to the `b` app.

```scala
import zio.http._

val a = Http.fail(new Error("SERVER_ERROR"))
val b = Http.text("OK")

val app = a <> b
```

### ZIO Integration

For creating effectful apps, you can use `collectZIO` and wrap `Response` with `UIO` to produce ZIO effect value.

```scala
val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "hello" => UIO(Response.text("Hello World"))
}
```

### Accessing the Request

To access the request use `@` as it binds a matched pattern to a variable and can be used while creating a response.

```scala
import zio.http._

val app = Http.collectZIO[Request] {
    case req @ Method.GET -> !! / "fruits" / "a"  =>
      UIO(Response.text("URL:" + req.url.path.asString + " Headers: " + req.getHeaders))
    case req @ Method.POST -> !! / "fruits" / "a" =>
      req.bodyAsString.map(Response.text(_))
  }
```

### Testing

Since `Http` is a function of the form `A => ZIO[R, Option[E], B]` to test it you can simply call an `Http` like a function.

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
When we call the `app` with the `request` it calls the apply method of `Http` via `zio.test` package

## Socket

`Socket` is functional domain in ZIO HTTP. It provides constructors to create socket apps. 
A socket app is an app that handles WebSocket connections.

### Creating a socket app

Socket app can be created by using `Socket` constructors. To create a socket app, you need to create a socket that accepts `WebSocketFrame` and produces `ZStream` of `WebSocketFrame`.
Finally, we need to convert socketApp to `Response` using `toResponse`, so that we can run it like any other HTTP app.   
The below example shows a simple socket app, we are using `collect` which returns a stream with WebsSocketTextFrame "BAR" on receiving WebsSocketTextFrame "FOO".   

```scala
import zio.socket._

private val socket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Text("FOO") =>
    ZStream.succeed(WebSocketFrame.text("BAR"))
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "greet" / name => UIO(Response.text(s"Greetings {$name}!"))
    case Method.GET -> !! / "ws"           => socket.toResponse
  }
```

## Server

As we have seen how to create HTTP apps, the only thing left is to run an  HTTP server and serve requests.
ZIO HTTP provides a way to set configurations for your server. The server can be configured according to the leak detection level, request size, address etc. 

### Starting an HTTP App

To launch our app, we need to start the server on a port. The below example shows a simple HTTP app that responds with empty content and a `200` status code, deployed on port `8090` using `Server.start`.

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

## Examples

- [HTTP Server](https://zio.github.io/zio-http/docs/v1.x/examples/zio-http-basic-examples/http_server)
- [Advanced Server](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/advanced_server)
- [WebSocket Server](https://zio.github.io/zio-http/docs/v1.x/examples/zio-http-basic-examples/web-socket)
- [Streaming Response](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/stream-response)
- [HTTP Client](https://zio.github.io/zio-http/docs/v1.x/examples/zio-http-basic-examples/http_client)
- [File Streaming](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/stream-file)
- [Authentication](https://zio.github.io/zio-http/docs/v1.x/examples/advanced-examples/authentication)
