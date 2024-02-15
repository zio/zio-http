---
id: routes
title: Routes
---

`Routes` models a collection of routes, each of which is defined by a pattern and a handler. This data type can be thought of as modeling a routing table, which decides where to direct every endpoint in an API based on both method and path of the request.

Let's see an example of a simple `Routes` that has two routes:

```scala mdoc:compile-only
import zio.http._

Routes(
  Method.GET / "hello"        -> Handler.text("hello"),
  Method.GET / "health-check" -> Handler.ok,
)
```

## Building Routes

To build empty routes we have `Routes.empty` constructor:

```scala mdoc:silent
import zio.http._ 

val routes1 = Routes.empty
```

We can build routes with the `Routes.apply` constructor, which takes varargs of individual `Route` values:

```scala
object Routes {
  def apply[Env, Err](
    route : Route[Env, Err],
    routes: Route[Env, Err]*,
  ): Routes[Env, Err] = ???
}
```

Example:

```scala mdoc:compile-only
Routes(
  Method.GET / "hello"        -> Handler.text("hello"),
  Method.GET / "health-check" -> Handler.ok,
  Method.POST / "echo"        ->
    handler { req: Request =>
      req.body.asString.map(Response.text(_))
    }.sandbox,
)
```

Please note that in this example, we have used the DSL for constructing routes, which consists of two parts, the route pattern and the handler:

1. `RoutePattern`- The route pattern is responsible for matching the method and path of the incoming request.
2. `Handler`- The handler is responsible for producing a response to the matched request.

Although we can build `Route` values by using the constructors of `Route`, we may prefer to use the DSL for constructing routes which starts in `Method`.

Using the `/` operator of `Method`, we can construct route patterns, which can then be bound to handlers, to create a fully formed route:

```scala mdoc:silent
val routes2 = 
  Routes(
    Method.GET / "hello"   -> Handler.ok,
    Method.GET / "goodbye" -> Handler.ok
  )
```

Using the `Routes.fromIterable` constructor, we can build routes from an iterable of individual routes.

## Nested Routes

Routes can be nested, which means that we can have routes that are themselves collections of other routes. This is useful for organizing routes into hierarchical structures, and for sharing common paths accross routes.

Let's see an example of nested routes:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec.PathCodec._


val routes = 
  literal("nest1") /
    Routes.fromIterable(
      Chunk(
        Method.GET / "foo" -> Handler.text("foo"),
        Method.GET / "bar" -> Handler.text("bar"),
      ) ++
        Chunk(
          literal("nest2") / Routes(
            Method.GET / "baz" -> Handler.text("baz"),
            Method.GET / "qux" -> Handler.text("qux"),
          ),
          literal("nest2") / Routes(
            Method.GET / "quux" -> Handler.text("quux"),
            Method.GET / "corge" -> Handler.text("corge"),
          ),
        ).map(_.routes).flatten,
    )
```

## Combining Routes

The only way to combine two routes collections is to concatenate them using the `++` operator:

```scala mdoc:silent
val routes3 = routes1 ++ routes2
```

If the routes have any overlap in their route patterns, then those on the left-side will take 
precedence over those on the right-hand side.

## Transforming Routes

Since routes are just a collection of individual routes, we can transform them in all the same ways that we can transform an individual route. We could do this manually, by building new routes from the old collection of routes, but several convenient methods do this:

### `Routes#transform`

Takes a function of type `Handler[Env, Response, Request, Response] => Handler[Env1, Response, Request, Response]` and applies it to all routes:

```scala
class Routes[-Env, +Err] private (val routes: Chunk[zio.http.Route[Env, Err]]) { self =>
  def transform[Env1](
    f: Handler[Env, Response, Request, Response] => Handler[Env1, Response, Request, Response],
  ): Routes[Env1, Err] =
```

Let's add a delay to all routes:

```scala mdoc:reset
```

```scala mdoc:compile-only
import zio._
import zio.http._

val routes: Routes[Any, Response] = ???

routes.transform[Any] { handle =>
   handler { (request: Request) => 
     ZIO.sleep(1.second) *> handle(request)
   }
}
```

### Applying Middlewares

One of the most common ways to transform routes is to apply a middleware to them. A middleware is a function that takes a collection of routes and returns a new collection of routes. To apply a middleware to `Routes` we can use the `Routes#@@` method:

```scala
final class Routes[-Env, +Err] private (val routes: Chunk[zio.http.Route[Env, Err]]) { self =>
  def @@[Env1 <: Env](aspect: Middleware[Env1]): Routes[Env1, Err] =
    aspect(self)
}
```

Let's add a logging middleware to all routes:

```scala mdoc:compile-only
import zio._
import zio.http._

val routes: Routes[Any, Response] = ???

val newRoutes = routes @@ HandlerAspect.dropTrailingSlash
```

To learn more about middlewares, see the [Middleware](middleware.md) section.

## Converting `Routes` to `HttpApp`

`HttpApp[-R]` represents a fully-specified HTTP application that can be executed by the server.

When we are done building a collection of routes, our next step is typically to convert these routes into an HTTP application using the `Routes#toHttpApp` method, which we can then execute with the server.

Routes may have handled or unhandled errors.  If the error type of `Routes[Env, Err]` is equal to or a subtype of `Response`, we call this a route where all errors are handled. Otherwise, it's a route where some errors are unhandled.

For instance, a route of type `Route[Env, Throwable]` has not handled its errors by converting them into responses. Consequently, such unfinished routes cannot be converted into HTTP applications. We must first handle errors using the `handleError` or `handleErrorCause` methods.

By handling our errors, we ensure that clients interacting with our API will not encounter strange or unexpected responses, but will always be able to interact effectively with our web service, even in exceptional cases.

:::note
If we aim to automatically convert our failures into suitable responses, without revealing details about the specific nature of the errors, we can utilize `Routes#sandbox`. After addressing our errors in this manner, we can proceed to convert our routes into an HTTP application.
:::

## Running an App

ZIO HTTP server needs an `HttpApp[R]` for running. We can use `Server.serve()` method to bootstrap the server with
an `HttpApp[R]`:

```scala mdoc:compile-only
import zio._
import zio.http._

object HelloWorld extends ZIOAppDefault {
  val app: HttpApp[Any] = Handler.ok.toHttpApp

  override def run = Server.serve(app).provide(Server.default)
} 
```
