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

### Through Handler Transformations

Takes a function of type `Handler[Env, Response, Request, Response] => Handler[Env1, Response, Request, Response]` and applies it to all routes:

```scala
trait Routes[-Env, +Err] {
  def transform[Env1](
    f: Handler[Env, Response, Request, Response] => Handler[Env1, Response, Request, Response],
  ): Routes[Env1, Err] = ???
}  
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

### Through Applying Middlewares

One of the most common ways to transform routes is to apply a middleware to them. A middleware is a function that takes a collection of routes and returns a new collection of routes. To apply a middleware to `Routes` we can use the `Routes#@@` method:

```scala
trait Routes[-Env, +Err] {
  def @@[Env1 <: Env](aspect: Middleware[Env1]): Routes[Env1, Err]
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

## Handling Errors in Routes

Like `ZIO` data type, we can handle errors in `Routes`. When we handle errors at the `Routes` level, we are handling errors that occur in any of the routes within the `Routes` data type.

The following methods are available for error handling:

```scala
trait Routes[-Env, +Err] {
  def handleError(f: Err => Response): Routes[Env, Nothing]
  def handleErrorCause(f: Cause[Err] => Response): Routes[Env, Nothing]
  def handleErrorCauseZIO(f: Cause[Err] => ZIO[Any, Nothing, Response]): Routes[Env, Nothing]
}
```

All of these methods are similar to their `ZIO` counterparts, i.e. `ZIO#catch*`, but they are applied to the routes.

If we need to take into account what request caused the error, we can use the following methods, instead:

```scala
trait Routes[-Env, +Err] {
  def handleErrorRequest(f: (Err, Request) => Response): Routes[Env, Nothing]
  def handleErrorRequestCause(f: (Request, Cause[Err]) => Response): Routes[Env, Nothing]
  def handleErrorRequestCauseZIO(f: (Request, Cause[Err]) => ZIO[Any, Nothing, Response]): Routes[Env, Nothing]
}
```

## Running an App

ZIO HTTP server needs `Routes[Env, Response]` for running, so routes that have a `Response` as the error type.
We can use `Server.serve()` method to bootstrap the server with an instance of `Routes[Env, Response]`.:

```scala mdoc:compile-only
import zio._
import zio.http._

object HelloWorld extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Handler.ok.toRoutes

  override def run = Server.serve(routes).provide(Server.default)
} 
```
