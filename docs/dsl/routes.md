---
id: routes
title: Routes
---

`Routes` models a collection of routes, each of which is defined by a pattern and a handler. 
This data type can be thought of as modeling a routing table,  which decides where to direct 
every endpoint in an API based on both method and path of the request.

When you are done building a collection of routes, you typically convert the routes into an 
HTTP application, which you can then execute with the server.

Routes may have handled or unhandled errors. A route of type `Route[Env, Throwable]`, for example, 
has not handled its errors by converting them into* responses. Such unfinished routes cannot yet 
be converted into HTTP applications. First, you must handle errors with the `handleError` or `handleErrorCause` methods.

## Building Routes

You can build routes with the `Routes.apply` constructor, which takes varargs of individual `Route` values, or you can build empty routes with `Routes.empty`:

```scala mdoc:silent
import zio._
import zio.http._ 

val routes1 = Routes.empty
```

Although you can build `Route` values by using the constructors of `Route`, you may prefer to use the DSL for constructing routes which starts in `Method`.

Using the `/` operator of `Method`, you can construct route patterns, which can then be bound to handlers, to create a fully formed route.

```scala mdoc:silent
val routes2 = 
  Routes(
    Method.GET / "hello"   -> Handler.ok,
    Method.GET / "goodbye" -> Handler.ok
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

Since routes are just a collection of individual routes, you can transform them in all the same
ways that you can transform an individual route. You could do this manually, by building new 
routes from the old collection of routes, but there are several convenient methods that do 
this for you:

 - `Routes#handleError` - Handles the error of all routes
 - `Routes#timeout` - Times out all routes
 - `Routes#@@` -- Transforms all routes

# What is HttpApp?

`HttpApp[-R]` represents a fully-specified HTTP application that can be executed by the server.

## Special Constructors Handler

These are some special constructors for `Http` and `Handler`:

### Handler.ok

Creates a `Handler` that always responds with a 200 status code.

```scala mdoc:silent
Handler.ok
```

### Handler.text

Creates a `Handler` that always responds with the same plain text.

```scala mdoc:silent
Handler.text("Text Response")
```

### Handler.status

Creates a `Handler` that always responds with the same status code and empty data.

```scala mdoc:silent
Handler.status(Status.Ok)
```

### Handler.error

Creates a `Handler` that always fails with the given `HttpError`.

```scala mdoc:silent
Handler.error(HttpError.Forbidden())
```

### Handler.response

Creates an `Handler` that always responds with the same `Response`.

```scala mdoc:silent
Handler.response(Response.ok)
```

## Special operators on Handler

These are some special operators for `Handler`s.

### method

Overwrites the method in the incoming request to the `Handler`

```scala mdoc:silent
val handler11 = Handler.fromFunction((request: Request) => Response.text(request.method.toString))
handler11.method(Method.POST)
```

### patch

Patches the response produced by the request handler using a `Patch`.

```scala mdoc:silent
val handler12 = Handler.response(Response.text("Hello World!"))
val handler13 = handler12.patch(Response.Patch.status(Status.Accepted))
```

## Converting `Routes` to `HttpApp`

If you want to deploy your routes on the ZIO HTTP server, you first need to convert it to `HttpApp[R]` using
`Routes#toHttpApp`.

Before you do this, you must first handle any typed errors produced by your routes by using `Routes#handleError`.

Handling your errors ensures that the clients of your API will not encounter strange and unexpected responses, but will always be able to usefully interact with your web service, even in exceptional cases.

If you wish to convert your errors into `InternalServerError`, without leaking any details on the specific nature of the errors,  you can use `Routes#ignore`, and after dealing with your errors in this way, you can convert your routes into an HTTP application.

## Running an App

ZIO HTTP server needs an `HttpApp[R]` for running. We can use `Server.serve()` method to bootstrap the server with
an `App[R]`:

```scala mdoc:silent
object HelloWorld extends ZIOAppDefault {
  val app: HttpApp[Any] = Handler.ok.toHttpApp

  override def run = Server.serve(app).provide(Server.default)
} 
```
