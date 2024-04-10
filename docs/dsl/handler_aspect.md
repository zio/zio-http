---
id: handler_aspect
title: HandlerAspect
---

A `HandlerAspect` is a wrapper around `ProtocolStack` with the two following features:

- It is a `ProtocolStack` that only works with `Request` and `Response` types. So it is suitable for writing middleware in the context of HTTP protocol. So it can almost be thought of (not the same) as a `ProtocolStack[Env, Request, Request, Response, Response]]`.

- It is specialized to work with an output context `CtxOut` that can be passed through the middleware stack. This allows each layer to add its output context to the transformation process. So the `CtxOut` will be a tuple of all the output contexts that each layer in the stack has added. These output contexts are useful when we are writing middleware that needs to pass some information, which is the result of some computation based on the input request, to the handler that is at the end of the middleware stack.

Now, we are ready to see the definition of `HandlerAspect`:

```scala
final case class HandlerAspect[-Env, +CtxOut](
  protocol: ProtocolStack[Env, Request, (Request, CtxOut), Response, Response]
) extends Middleware[Env] {
    def apply[Env1 <: Env, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] = ???
}
```

Like the `ProtocolStack`, the `HandlerAspect` is a stack of layers. When we compose two `HandlerAspect` using the `++` operator, we are composing middlewares sequentially. So each layer in the stack corresponds to a separate transformation.

Similar to the `ProtocolStack`, each layer in the `HandlerAspect` may also be stateful at the level of each transformation. So, for example, a layer that is timing request durations may capture the start time of the request in the incoming interceptor, and pass this state to the outgoing interceptor, which can then compute the duration.

## Creating a HandlerAspect

The `HandlerAspect`'s companion object provides many methods to create a `HandlerAspect`. But in this section, we are going to introduce the most basic ones that are used as a building block to create a more complex `HandlerAspect`.

The `HandlerAspect.identity` is the simplest `HandlerAspect` that does nothing. It is useful when you want to create a `HandlerAspect` that does not modify the request or response.

After this simple `HandlerAspect`, let's dive into the `HandlerAspect.intercept*` constructors. Using these, we can create a `HandlerAspect` that can intercept the incoming request, outgoing response, or both.

## Intercepting

### Intercepting the Incoming Requests

The `HandlerAspect.interceptIncomingHandler` constructor takes a handler function and applies it to the incoming request. It is useful when we want to modify or access the request before it reaches the handler or the next layer in the stack.

Let's see an example of how to use this constructor to create a middleware that checks the IP address of the incoming request and allows only the whitelisted IP addresses to access the server:

```scala mdoc:compile-only
import zio._
import zio.http._

val whitelistMiddleware: HandlerAspect[Any, Unit] =
  HandlerAspect.interceptIncomingHandler {
    val whitelist = Set("127.0.0.1", "0.0.0.0")
    Handler.fromFunctionZIO[Request] { request =>
      request.headers.get("X-Real-IP") match {
        case Some(host) if whitelist.contains(host) =>
          ZIO.succeed((request, ()))
        case _ =>
          ZIO.fail(Response.forbidden("Your IP is banned from accessing the server."))
      }
    }
  }
```

### Intercepting the Outgoing Responses

The `HandlerAspect.interceptOutgoingHandler` constructor takes a handler function and applies it to the outgoing response. It is useful when we want to modify or access the response before it reaches the client or the next layer in the stack.

Let's work on creating a middleware that adds a custom header to the response:

```scala mdoc:compile-only
import zio.http._

val addCustomHeader: HandlerAspect[Any, Unit] =
  HandlerAspect.interceptOutgoingHandler(
    Handler.fromFunction[Response](_.addHeader("X-Custom-Header", "Hello from Custom Middleware!")),
  )
```

The `interceptOutgoingHandler` takes a handler function that receives a `Response` and returns a `Response`. This is simpler than the `interceptIncomingHandler` as it does not necessitate the output context to be passed along with the response.

### Intercepting Both Incoming Requests and Outgoing Responses

The `HandlerAspect.interceptHandler` takes two handler functions, one for the incoming request and one for the outgoing response.

In the following example, we are going to create a middleware that counts the number of incoming requests and outgoing responses and stores them in a `Ref` inside the ZIO environment:

```scala mdoc:compile-only
import zio._
import zio.http._

def inc(label: String) =
  for {
    counter <- ZIO.service[Ref[Map[String, Long]]]
    _ <- counter.update(_.updatedWith(label) {
      case Some(current) => Some(current + 1)
      case None => Some(1)
    })
  } yield ()

val countRequests: Handler[Ref[Map[String, Long]], Nothing, Request, (Request, Unit)] =
  Handler.fromFunctionZIO[Request](request => inc("requests").as((request, ())))

val countResponses: Handler[Ref[Map[String, Long]], Nothing, Response, Response] =
  Handler.fromFunctionZIO[Response](response => inc("responses").as(response))

val counterMiddleware: HandlerAspect[Ref[Map[String, Long]], Unit] =
  HandlerAspect.interceptHandler(countRequests)(countResponses)
```

Then, we can write another middleware that is responsible for adding a route to get the statistics of the incoming requests and outgoing responses:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.schema.codec.JsonCodec.zioJsonBinaryCodec

val statsMiddleware: Middleware[Ref[Map[String, Long]]] =
  new Middleware[Ref[Map[String, Long]]] {
    override def apply[Env1 <: Ref[Map[String, Long]], Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
      routes ++ Routes(
        Method.GET / "stats" -> Handler.fromFunctionZIO[Request] { _ =>
          ZIO.serviceWithZIO[Ref[Map[String, Long]]](_.get).map(stats => Response(body = Body.from(stats)))
        },
      )
  }
```

After attaching these two middlewares to our HttpApp, we have to provide the initial state for the `Ref[Map[String, Long]]` to the whole application's environment:

```scala
Server.serve(app @@ counterMiddleware @@ statsMiddleware)
  .provide(
    Server.default,
    ZLayer.fromZIO(Ref.make(Map.empty[String, Long]))
  )
```

### Intercepting Statefully

The `HandlerAspect.interceptHandlerStateful` constructor is like the `interceptHandler`, but it allows the incoming handler to have a state that can be passed to the next layer in the stack, and finally, that state can be accessed by the outgoing handler.

Here is how it works:

1. The incoming handler receives a `Request` and produces a tuple of `State` and `(Request, CtxOut)`.
2. The state produced by the incoming handler is passed to the next layer in the stack.
3. The outgoing handler receives the `State` along with the `Response` as a tuple, i.e. `(State, Response)`, and produces a `Response`.

So, we can pass some state from the incoming handler to the outgoing handler.

In the following example, we are going to write a middleware that calculates the response time and includes it in the `X-Response-Time` header:

```scala mdoc:compile-only
import zio._
import zio.http._
import java.util.concurrent.TimeUnit

val incomingTime: Handler[Any, Nothing, Request, (Long, (Request, Unit))] =
  Handler.fromFunctionZIO(request => ZIO.clockWith(_.currentTime(TimeUnit.MILLISECONDS)).map(t => (t, (request, ()))))

val outgoingTime: Handler[Any, Nothing, (Long, Response), Response] =
  Handler.fromFunctionZIO { case (incomingTime, response) =>
    ZIO
      .clockWith(_.currentTime(TimeUnit.MILLISECONDS).map(t => t - incomingTime))
      .map(responseTime => response.addHeader("X-Response-Time", s"${responseTime}ms"))
  }

val responseTime: HandlerAspect[Any, Unit] =
  HandlerAspect.interceptHandlerStateful(incomingTime)(outgoingTime)
```

By attaching this middleware to any route, we can see the response time in the `X-Response-Time` header:

```bash
$ curl -X GET 'http://127.0.0.1:8080/hello' -i
HTTP/1.1 200 OK
content-type: text/plain
X-Response-Time: 100ms
content-length: 12

Hello World!⏎
```

### Intercepting Statefully (Patching Responses)

Sometimes we want to apply a series of transformations to the outgoing response. We can use the `HandlerAspect.interceptPatch` and `HandlerAspect.interceptPatchZIO` to achieve this.

A `Response.Patch` is a data type that represents a function (or series of functions) that can be applied to a response and return a new response. The `HanlderAspect.interceptPatch*` uses this data type to transform the response.

The `HandlerApect.interceptPatch` takes two groups of arguments:

1. **Intercepting the Incoming Request**: The first one is a function that takes the incoming `Request` and produces a `State`. This state is passed through the middleware stack and then can be accessed through the interception phase of the outgoing response.
2. **Intercepting the Outgoing Response**: The second one is a function that takes a tuple of `Response` and `State` and returns a `Response.Patch` that will be applied to the outgoing response.

Let's try to rewrite the previous example using the `HandlerAspect.interceptPatch`:

```scala mdoc:compile-only
import zio._
import zio.http._
import java.util.concurrent.TimeUnit

val incomingTime: Request => ZIO[Any, Nothing, Long] =
  (_: Request) => ZIO.clockWith(_.currentTime(TimeUnit.MILLISECONDS))

val outgoingTime: (Response, Long) => ZIO[Any, Nothing, Response.Patch] =
  (_: Response, incomingTime: Long) =>
    ZIO
      .clockWith(_.currentTime(TimeUnit.MILLISECONDS).map(t => t - incomingTime))
      .map(responseTime => Response.Patch.addHeader("X-Response-Time", s"${responseTime}ms"))

val responseTime: HandlerAspect[Any, Unit] =
  HandlerAspect.interceptPatchZIO(incomingTime)(outgoingTime)
```

## Leveraging Output Context

When writing a middleware, in some cases, we want to pass some information from the middleware to the request handler which is at the end of the stack.

If we take a look at the definition of `HandlerAspect`, we can see that it has two type parameters, `Env` and `CtxOut`. The `CtxOut` is the output context. When we don't need to pass any context to the output, we use `Unit` as the output context, otherwise, we can utilize any type as the output context.

Before diving into a real-world example, let's try to understand the output context with simple examples. First, assume that we have an identity `HandlerAspect` that does nothing but passes an integer value to the output context:

```scala mdoc:silent:reset
import zio.http._

val intAspect: HandlerAspect[Any, Int] = HandlerAspect.identity.as(42)
```

To access this integer value in the handler, we need to define a handler that receives a tuple of `(Int, Request)`:

```scala mdoc:silent
val intRequestHandler: Handler[Any, Nothing, (Int, Request), Response] =
  Handler.fromFunction[(Int, Request)] { case (n, _) =>
    Response.text(s"Received the $n value from the output context!")
  }
```

If we attach the `intAspect` to this handler, we get back a handler that receives a `Request` and produces a `Response`:

```scala mdoc:compile-only
val handler: Handler[Any, Response, Request, Response] = 
  intRequestHandler @@ intAspect
```

Another thing to note is that when we compose multiple `HandlerAspect`s with output context of non-`Unit` type, the output context of composed `HandlerAspect` will be a tuple of all the output contexts:

```scala mdoc:silent
val stringAspect: HandlerAspect[Any, String] = 
  HandlerAspect.identity.as("Hello, World!")

val intStringAspect: HandlerAspect[Any, (Int, String)] = 
  intAspect ++ stringAspect
```

Correspondingly, to access the output context of this `HandlerAspect`, we need to have a handler that receives a tuple of `(Int, String, Request)`:

```scala mdoc:silent
val intStringRequestHandler: Handler[Any, Nothing, (Int, String, Request), Response] =
  Handler.fromFunction[(Int, String, Request)] { case (n, s, _) =>
    Response.text(s"Received the $n and $s values from the output context!")
  }
```

Finally, we can attach the `intStringAspect` to this handler:

```scala mdoc:silent
val handler: Handler[Any, Response, Request, Response] = 
  intStringRequestHandler @@ (intAspect ++ stringAspect)
```

### Custom Authentication Example

Now, let's see a real-world example where we can leverage the output context.

In the following example, we are going to write an authentication middleware that checks the JWT token in the incoming request and passes the user information to the handler:

```scala mdoc:silent
import zio._
import zio.http._
import scala.util.Try
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

// Secret Authentication key
val SECRET_KEY = "secretKey"

def jwtDecode(token: String, key: String): Try[JwtClaim] =
  Jwt.decode(token, key, Seq(JwtAlgorithm.HS512))

val bearerAuthWithContext: HandlerAspect[Any, String] =
  HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
    request.header(Header.Authorization) match {
      case Some(Header.Authorization.Bearer(token)) =>
        ZIO
          .fromTry(jwtDecode(token.value.asString, SECRET_KEY))
          .orElseFail(Response.badRequest("Invalid or expired token!"))
          .flatMap(claim => ZIO.fromOption(claim.subject).orElseFail(Response.badRequest("Missing subject claim!")))
          .map(u => (request, u))

      case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))))
    }
  })
```

Now, let's define the `/profile/me` route that requires authentication output context:

```scala mdoc:compile-only
val profileRoute: Route[Any, Response] =
  Method.GET / "profile" / "me" -> 
    Handler.fromFunction[(String, Request)] { case (name: String, _: Request) => 
      Response.text(s"Welcome $name!")
  } @@ bearerAuthWithContext
```

That's it! Now, in the handler of the `/profile/me` route, we have the username that is extracted from the JWT token inside the authentication middleware and passed to it.

The following code snippet is the complete example. Using the login route, we can get the JWT token and use it to access the protected `/profile/me` route:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/AuthenticationServer.scala")
```

After running the server, we can test it using the following client code:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/AuthenticationClient.scala")
```

## Authentication HandlerAspects

There are several built-in `HandlerAspect`s that can be used to implement authentication in ZIO HTTP:

1. **Basic Authentication**: The `basicAuth` and `basicAuthZIO` middleware can be used to implement basic authentication.
2. **Bearer Authentication**: The `bearerAuth` and `bearerAuthZIO` middleware can be used to implement bearer authentication. We have to provide a function that validates the bearer token.
3. **Custom Authentication**: The `customAuth`, `customAuthZIO`, `customAuthProviding`, and `customAuthProvidingZIO` handler aspects can be used to implement custom authentication. We have to provide a function that validates the request.

## Failing HandlerAspects

We can abort the requests by specific response using `HandlerAspect.fail` and `HandlerAspect.failWith` aspects, so the downstream handlers will not be executed:

```scala mdoc:invisible
val myHandler = Handler.identity
```

```scala mdoc:compile-only
import zio.http._

myHandler @@ HandlerAspect.fail(Response.forbidden("Access Denied!"))

myHandler @@ HandlerAspect
  .fail(Response.forbidden("Access Denied!"))
  .when(req => req.method == Method.DELETE)
```

## Updating Requests and Responses

Several aspects are useful for updating the requests and responses:

| Description             | HandlerAspect                                                     |
|-------------------------|-------------------------------------------------------------------|
| Update Request          | `HandlerAspect.updateRequest`, `HandlerAspect.updateRequestZIO`   |
| Update Request's Method | `HandlerAspect.updateMethod`                                      |
| Update Request's Path   | `HandlerAspect.updatePath`                                        |
| Update Request's URL    | `HandlerAspect.updateURL`                                         |
| Update Response         | `HandlerAspect.updateResponse`, `HandlerAspect.updateResponseZIO` |
| Update Response Headers | `HandlerAspect.updateHeaders`                                     |
| Update Response Status  | `HandlerAspect.status`                                            |

These aspects can be used to modify the request and response before they reach the handler or the client. They take a function that transforms the request or response and returns the updated request or response. Let's see an example:

```scala mdoc:compile-only
val dropTrailingSlash = HandlerAspect.updateURL(_.dropTrailingSlash) 
```

## Access Control HandlerAspects

To allow and disallow access to an HTTP based on some conditions, we can use the `HandlerAspect.allow` and `HandlerAspect.allowZIO` aspects.

```scala mdoc:compile-only
val disallow: HandlerAspect[Any, Unit] = HandlerAspect.allow(_ => false)
val allow: HandlerAspect[Any, Unit]    = HandlerAspect.allow(_ => true)

val whitelistAspect: HandlerAspect[Any, Unit] = {
  val whitelist = Set("127.0.0.1", "0.0.0.0")
  HandlerAspect.allow(r =>
    r.headers.get("X-Real-IP") match {
      case Some(host) => whitelist.contains(host)
      case None       => false
    },
  )
}
```

## Cookie Operations

Several aspects are useful for adding, signing, and managing cookies:

1. `HandlerAspect.addCookie` and `HandlerAspect.addCookieZIO` to add cookies
2. `HandlerAspect.signCookies` to sign cookies
3. `HandlerAspect.flashScopeHandling` to manage the flash scope

## Conditional Application of Middlewares

We can attach a handler aspect conditionally using `HandlerAspect#when`, `HandlerAspect#whenZIO`, and `HandlerAspect#whenHeader` methods. Wen also uses the following constructors to have conditional handler aspects: `HandlerAspect.when`, `HandlerAspect.whenZIO`, `HandlerAspect.whenHeader`, `HandlerAspect.whenResponse`, and `HandlerAspect.whenResponseZIO`.

We have also some `if-then-else` style constructors to create conditional aspects like `HandlerAspect.ifHeaderThenElse`, `HandlerAspect.ifMethodThenElse`, `HandlerAspect.ifRequestThenElse`, and `HandlerAspect.ifRequestThenElseZIO`.

## Other Built-in HandlerAspects

ZIO HTTP offers a versatile set of built-in middlewares, designed to enhance and customize the handling of HTTP requests and responses. These middlewares can be easily integrated into our application to provide various functionalities. Until now, we have seen several built-in aspects, here are some other built-in aspects:

| HandlerAspect                       | Description                                            |
|-------------------------------------|--------------------------------------------------------|
| `beautifyErrors`                    | Beautify Error Response                                |
| `debug`                             | Debugging Requests and Responses                       |
| `dropTrailingSlash`                 | Drop Trailing Slash                                    |
| `identity`                          | Identity Middleware (No effect on request or response) |
| `patch`, `patchZIO`                 | Patch Middleware                                       |
| `redirect`, `redirectTrailingSlash` | Redirect Middleware                                    |
| `requestLogging`                    | Request Logging Middleware                             |
| `runBefore`, `runAfter`             | Running Effect Before/After Every Request              |

## Examples

### A simple middleware example

Let us consider a simple example using out-of-the-box middleware called ```addHeader```. We will write a middleware that will attach a custom header to the response.

We create a middleware that appends an additional header to the response indicating whether it is a Dev/Prod/Staging environment:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithMiddlewares.scala")
```

Fire a curl request, and we see an additional header added to the response indicating the "Dev" environment:

```bash
curl -i http://localhost:8080/Bob

HTTP/1.1 200 OK
content-type: text/plain
X-Environment: Dev
content-length: 12

Hello Bob
```

### CORS Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithCORS.scala")
```

### Bearer Authentication Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/AuthenticationServer.scala")
```

### Basic Authentication Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/BasicAuth.scala")
```

To the example, start the server and fire a curl request with an incorrect user/password combination:

```bash
curl -i --user admin:wrong http://localhost:8080/user/admin/greet

HTTP/1.1 401 Unauthorized
www-authenticate: Basic
content-length: 0
```

We notice in the response that first basicAuth middleware responded `HTTP/1.1 401 Unauthorized` and then patch middleware attached a `X-Environment: Dev` header. 

### Endpoint Middleware Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/EndpointExamples.scala")
```

### Serving Static Files Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StaticFiles.scala")
```
